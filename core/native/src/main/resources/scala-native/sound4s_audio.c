/*
 * A small blocking PCM capture/playback API over miniaudio, for the Scala
 * Native backend of sound4s.
 *
 * miniaudio drives the device from its own audio thread, through a callback
 * that only touches a byte ring buffer; Scala reads and writes the other end
 * of that buffer with blocking calls. No call crosses back into Scala from
 * the audio thread.
 */
#define MA_NO_DECODING
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#include <stdlib.h>
#include <string.h>
#include <time.h>

#define S4S_OK 0
#define S4S_NOT_FOUND 1
#define S4S_UNSUPPORTED 2
#define S4S_FAILED 3

typedef struct {
    ma_context context;
    ma_device device;
    ma_mutex lock;
    ma_uint8 *buffer;
    ma_uint32 capacity; /* bytes, a multiple of frameSize */
    ma_uint32 head;     /* index of the oldest byte */
    ma_uint32 size;     /* bytes held */
    ma_uint32 frameSize;
    ma_format format;
    ma_uint32 channels;
    int playback;
} s4s_stream;

static void sleep_1ms(void) {
    struct timespec ts = {0, 1000000L};
    nanosleep(&ts, NULL);
}

/* Appends up to len bytes; drops the oldest bytes if the buffer is full.
 * Caller holds the lock. */
static void push_overwriting(s4s_stream *s, const ma_uint8 *src, ma_uint32 len) {
    if (len >= s->capacity) {
        src += len - s->capacity;
        len = s->capacity;
        s->head = 0;
        s->size = 0;
    }
    if (s->size + len > s->capacity) {
        ma_uint32 drop = s->size + len - s->capacity;
        s->head = (s->head + drop) % s->capacity;
        s->size -= drop;
    }
    ma_uint32 tail = (s->head + s->size) % s->capacity;
    ma_uint32 first = s->capacity - tail;
    if (first > len) first = len;
    memcpy(s->buffer + tail, src, first);
    memcpy(s->buffer, src + first, len - first);
    s->size += len;
}

/* Appends up to len bytes without dropping; returns the number appended.
 * Caller holds the lock. */
static ma_uint32 push(s4s_stream *s, const ma_uint8 *src, ma_uint32 len) {
    ma_uint32 space = s->capacity - s->size;
    if (len > space) len = space;
    if (len > 0) push_overwriting(s, src, len);
    return len;
}

/* Removes up to len bytes; returns the number removed. Caller holds the lock. */
static ma_uint32 pop(s4s_stream *s, ma_uint8 *dst, ma_uint32 len) {
    if (len > s->size) len = s->size;
    ma_uint32 first = s->capacity - s->head;
    if (first > len) first = len;
    memcpy(dst, s->buffer + s->head, first);
    memcpy(dst + first, s->buffer, len - first);
    s->head = (s->head + len) % s->capacity;
    s->size -= len;
    return len;
}

static void data_callback(ma_device *device, void *output, const void *input,
                          ma_uint32 frameCount) {
    s4s_stream *s = (s4s_stream *)device->pUserData;
    ma_uint32 bytes = frameCount * s->frameSize;
    ma_mutex_lock(&s->lock);
    if (s->playback) {
        ma_uint32 want = bytes;
        ma_uint32 got = pop(s, (ma_uint8 *)output, want);
        got -= got % s->frameSize;
        if (got < want) {
            ma_silence_pcm_frames((ma_uint8 *)output + got,
                                  (want - got) / s->frameSize, s->format,
                                  s->channels);
        }
    } else {
        push_overwriting(s, (const ma_uint8 *)input, bytes);
    }
    ma_mutex_unlock(&s->lock);
}

static int to_format(int bitDepth, int isSigned, ma_format *out) {
    if (bitDepth == 8 && !isSigned) { *out = ma_format_u8; return 1; }
    if (bitDepth == 16 && isSigned) { *out = ma_format_s16; return 1; }
    if (bitDepth == 24 && isSigned) { *out = ma_format_s24; return 1; }
    if (bitDepth == 32 && isSigned) { *out = ma_format_s32; return 1; }
    return 0;
}

/* Writes the names of the playback (or capture) devices, one per line, into
 * out (NUL-terminated, truncated to outLen). Returns S4S_OK or S4S_FAILED. */
int s4s_list_devices(int playback, char *out, int outLen) {
    ma_context context;
    ma_device_info *playbackInfos, *captureInfos;
    ma_uint32 playbackCount, captureCount;
    if (outLen <= 0) return S4S_FAILED;
    out[0] = '\0';
    if (ma_context_init(NULL, 0, NULL, &context) != MA_SUCCESS) return S4S_FAILED;
    if (ma_context_get_devices(&context, &playbackInfos, &playbackCount,
                               &captureInfos, &captureCount) != MA_SUCCESS) {
        ma_context_uninit(&context);
        return S4S_FAILED;
    }
    ma_device_info *infos = playback ? playbackInfos : captureInfos;
    ma_uint32 count = playback ? playbackCount : captureCount;
    int used = 0;
    for (ma_uint32 i = 0; i < count; i++) {
        int n = (int)strlen(infos[i].name);
        if (used + n + 2 > outLen) break;
        memcpy(out + used, infos[i].name, n);
        out[used + n] = '\n';
        used += n + 1;
        out[used] = '\0';
    }
    ma_context_uninit(&context);
    return S4S_OK;
}

/* Opens and starts a stream on the device called name (the default device if
 * name is NULL). bufferMillis is the ring buffer's length. */
int s4s_open(s4s_stream **result, int playback, const char *name,
             int sampleRate, int channels, int bitDepth, int isSigned,
             int bufferMillis) {
    ma_format format;
    if (!to_format(bitDepth, isSigned, &format)) return S4S_UNSUPPORTED;

    s4s_stream *s = (s4s_stream *)calloc(1, sizeof(s4s_stream));
    if (s == NULL) return S4S_FAILED;
    s->playback = playback;
    s->format = format;
    s->channels = (ma_uint32)channels;
    s->frameSize = ma_get_bytes_per_frame(format, (ma_uint32)channels);
    s->capacity = (ma_uint32)((long long)sampleRate * bufferMillis / 1000) *
                  s->frameSize;
    if (s->capacity == 0) s->capacity = s->frameSize;
    s->buffer = (ma_uint8 *)malloc(s->capacity);
    if (s->buffer == NULL) { free(s); return S4S_FAILED; }
    if (ma_mutex_init(&s->lock) != MA_SUCCESS) {
        free(s->buffer); free(s); return S4S_FAILED;
    }
    if (ma_context_init(NULL, 0, NULL, &s->context) != MA_SUCCESS) {
        ma_mutex_uninit(&s->lock); free(s->buffer); free(s); return S4S_FAILED;
    }

    ma_device_id id;
    ma_device_id *pId = NULL;
    if (name != NULL) {
        ma_device_info *playbackInfos, *captureInfos;
        ma_uint32 playbackCount, captureCount;
        if (ma_context_get_devices(&s->context, &playbackInfos, &playbackCount,
                                   &captureInfos, &captureCount) != MA_SUCCESS) {
            goto failed;
        }
        ma_device_info *infos = playback ? playbackInfos : captureInfos;
        ma_uint32 count = playback ? playbackCount : captureCount;
        for (ma_uint32 i = 0; i < count; i++) {
            if (strcmp(infos[i].name, name) == 0) {
                id = infos[i].id;
                pId = &id;
                break;
            }
        }
        if (pId == NULL) {
            ma_context_uninit(&s->context);
            ma_mutex_uninit(&s->lock);
            free(s->buffer);
            free(s);
            return S4S_NOT_FOUND;
        }
    }

    ma_device_config config = ma_device_config_init(
        playback ? ma_device_type_playback : ma_device_type_capture);
    config.sampleRate = (ma_uint32)sampleRate;
    config.dataCallback = data_callback;
    config.pUserData = s;
    if (playback) {
        config.playback.pDeviceID = pId;
        config.playback.format = format;
        config.playback.channels = (ma_uint32)channels;
    } else {
        config.capture.pDeviceID = pId;
        config.capture.format = format;
        config.capture.channels = (ma_uint32)channels;
    }
    if (ma_device_init(&s->context, &config, &s->device) != MA_SUCCESS) {
        goto failed;
    }
    if (ma_device_start(&s->device) != MA_SUCCESS) {
        ma_device_uninit(&s->device);
        goto failed;
    }
    *result = s;
    return S4S_OK;

failed:
    ma_context_uninit(&s->context);
    ma_mutex_uninit(&s->lock);
    free(s->buffer);
    free(s);
    return S4S_FAILED;
}

/* Reads up to bytes, waiting at most timeoutMs for them. Returns the number
 * of bytes read. */
int s4s_read(s4s_stream *s, ma_uint8 *dst, int bytes, int timeoutMs) {
    int got = 0, waited = 0;
    while (got < bytes) {
        ma_mutex_lock(&s->lock);
        ma_uint32 n = pop(s, dst + got, (ma_uint32)(bytes - got));
        ma_mutex_unlock(&s->lock);
        got += (int)n;
        if (got >= bytes) break;
        if (n == 0) {
            if (waited >= timeoutMs) break;
            sleep_1ms();
            waited++;
        }
    }
    return got;
}

/* Writes up to bytes, waiting at most timeoutMs for room. Returns the number
 * of bytes written. */
int s4s_write(s4s_stream *s, const ma_uint8 *src, int bytes, int timeoutMs) {
    int put = 0, waited = 0;
    while (put < bytes) {
        ma_mutex_lock(&s->lock);
        ma_uint32 n = push(s, src + put, (ma_uint32)(bytes - put));
        ma_mutex_unlock(&s->lock);
        put += (int)n;
        if (put >= bytes) break;
        if (n == 0) {
            if (waited >= timeoutMs) break;
            sleep_1ms();
            waited++;
        }
    }
    return put;
}

/* Discards the buffered audio. */
void s4s_flush(s4s_stream *s) {
    ma_mutex_lock(&s->lock);
    s->head = 0;
    s->size = 0;
    ma_mutex_unlock(&s->lock);
}

/* Stops and frees the stream. A playback stream first plays what it holds,
 * for at most drainTimeoutMs. */
void s4s_close(s4s_stream *s, int drainTimeoutMs) {
    if (s->playback) {
        int waited = 0;
        for (;;) {
            ma_mutex_lock(&s->lock);
            ma_uint32 left = s->size;
            ma_mutex_unlock(&s->lock);
            if (left < s->frameSize || waited >= drainTimeoutMs) break;
            sleep_1ms();
            waited++;
        }
    }
    ma_device_uninit(&s->device);
    ma_context_uninit(&s->context);
    ma_mutex_uninit(&s->lock);
    free(s->buffer);
    free(s);
}
