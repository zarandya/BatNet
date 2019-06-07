#include <jni.h>
#include <complex.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <fftw3.h>
#include "android-log.h"

typedef struct {
		jdouble base_frequency;
		jint base_frequency_factor;
		jint start_index;
		jint end_index;
		jint window_size;
		jint samplerate;
		jint receiver_buffer_size;

		int remaining;
		int current_index;
		double complex acc;
		double phase;

		fftw_plan p;
		double* fft_in;
		double complex* fft_out;

		void* results; // 2D array, diagonal signal, elsewhere noise
} native_struct;

jfieldID ReceiverCalibration_nativePointer;

void populate_native_struct(
		native_struct* ns,
		jdouble base_frequency,
		jint start_index,
		jint end_index,
		jint window_size,
		jint samplerate,
		jint receiver_buffer_size
) {

	double* in = fftw_malloc(window_size * sizeof(double));
	double complex* out = fftw_malloc((window_size / 2 + 1) * sizeof(double complex));
	fftw_plan p = fftw_plan_dft_r2c_1d(window_size, in, out, FFTW_ESTIMATE);

	void* results = malloc((end_index - start_index + 1) * (end_index - start_index + 1) * sizeof(double));

	*ns = (native_struct) {
		.base_frequency = base_frequency,
		.base_frequency_factor = base_frequency * window_size / samplerate,
		.start_index = start_index,
		.end_index = end_index,
		.window_size = window_size,
		.samplerate = samplerate,
		.receiver_buffer_size = receiver_buffer_size,
	
		.remaining = 0,
		.current_index = 0,

		.p = p,
		.fft_in = in,
		.fft_out = out,

		.results = results
	};

	__android_log_print(ANDROID_LOG_DEBUG, "Calib", "populate native calib base_frequency=%f, base_frequency_factor=%d, start_index=%d, end_index=%d, window_size=%d, samplerate=%d", base_frequency, ns->base_frequency_factor, start_index, end_index, window_size, samplerate);
}

void free_native_buffers(native_struct* ns) {
	free(ns->results);
	fftw_free(ns->fft_in);
	fftw_free(ns->fft_out);
	fftw_free(ns->p);
}

JNIEXPORT void JNICALL
Java_org_batnet_calib_ReceiverCalibration_updateNativeStruct(
		JNIEnv* env, 
		jobject this, 
		jdouble base_frequency,
		jint start_index,
		jint end_index,
		jint window_size,
		jint samplerate,
		jint receiver_buffer_size
) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, ReceiverCalibration_nativePointer);
	free_native_buffers(ns);
	populate_native_struct(ns, base_frequency, start_index, end_index, window_size, samplerate, receiver_buffer_size);
}

JNIEXPORT jlong JNICALL
Java_org_batnet_calib_ReceiverCalibration_nativeInit(
		JNIEnv* env,
		jobject this,
		jdouble base_frequency,
		jint start_index,
		jint end_index,
		jint window_size,
		jint samplerate,
		jint receiver_buffer_size
) {
	jclass class = (*env)->GetObjectClass(env, this);
	ReceiverCalibration_nativePointer = (*env)->GetFieldID(env, class, "nativePointer", "J");
	__android_log_print(ANDROID_LOG_DEBUG, "ReceiverCalibration", "INIT RECVCALIB");

	native_struct* ns = malloc(sizeof(native_struct));
	populate_native_struct(ns, base_frequency, start_index, end_index, window_size, samplerate, receiver_buffer_size);

	__android_log_print(ANDROID_LOG_DEBUG, "ReceiverCalibration", "INIT RECVCALIB DONE");
	return (jlong) ns;
}

double sqr_magnitude(double complex z) {
	return creal(z) * creal(z) + cimag(z) * cimag(z);
}

#define MIN(A, B) ((A) < (B) ? (A) : (B))
#define ARRAY2D(T, N, R, C, S) T (*N)[C] = (S)

JNIEXPORT jboolean JNICALL
Java_org_batnet_calib_ReceiverCalibration_addBuffer(
		JNIEnv* env,
		jobject this,
		jshortArray samples,
		jint offset
) {
	__android_log_print(ANDROID_LOG_DEBUG, "Calib", "START addBuffer");
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, ReceiverCalibration_nativePointer);
	jshort* buf = (*env)->GetShortArrayElements(env, samples, NULL);
	int len = ns->receiver_buffer_size;

	int d = ns->end_index - ns->start_index + 1;
	ARRAY2D(double, results, d, d, ns->results);

	double* target = ns->fft_in + ns->remaining;
	double* target_end = ns->fft_in + ns->window_size;
	short* source = buf + offset;
	short* source_end = buf + len;
	while (source < source_end) {
		__android_log_print(ANDROID_LOG_DEBUG, "Calib", "Extracting index %d (%f): %d", ns->current_index, ns->base_frequency * (ns->start_index + ns->current_index), ns->base_frequency_factor * (ns->start_index + ns->current_index));
		int remaining = ns->remaining;
		while (target < target_end && source < source_end) {
			*target = *source;
			++target;
			++source;
			++remaining;

			++offset;
		}
		__android_log_print(ANDROID_LOG_DEBUG, "Calib", "offset %d len %d", offset, len);

		if (remaining == ns->window_size) {
			ns->remaining = 0;

			fftw_execute(ns->p);

			for (int i = 0; i < d; ++i) {
				results[i][ns->current_index] = sqrt(sqr_magnitude(ns->fft_out[ns->base_frequency_factor * (ns->start_index + i)]));
				__android_log_print(ANDROID_LOG_DEBUG, "Calib", "[%d][%d]=%f", i, ns->current_index, results[i][ns->current_index]);
			}
			++ns->current_index;
			if (ns->current_index >= d) {
				ns->current_index = 0;
				__android_log_print(ANDROID_LOG_DEBUG, "Calib", "EXIT addBuffer");
				return 1;
			}
			target = ns->fft_in;
		}
		else {
			ns->remaining = remaining;
		}
	}

	(*env)->ReleaseShortArrayElements(env, samples, buf, 0);

	__android_log_print(ANDROID_LOG_DEBUG, "Calib", "FINISH addBuffer");
	return 0;
}

JNIEXPORT jdoubleArray JNICALL
Java_org_batnet_calib_ReceiverCalibration_getResults(
		JNIEnv* env,
		jobject this
) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, ReceiverCalibration_nativePointer);
	int d = ns->end_index - ns->start_index + 1;
	ARRAY2D(double, results, d, d, ns->results);

	double results1d[d];
	for (int i = 0; i < d; ++i) {
		double signal = results[i][i];
		double noise = -signal;
		for (int j = 0; j < d; ++j) {
			noise += results[i][j];
		}
		results1d[i] = signal / (noise / (d-1));
	}

	jdoubleArray jresults1d = (*env)->NewDoubleArray(env, d);
	(*env)->SetDoubleArrayRegion(env, jresults1d, 0, d, results1d);

	return jresults1d;
}

JNIEXPORT jdouble JNICALL
Java_org_batnet_calib_ReceiverCalibration_getAmplitude(
		JNIEnv* env,
		jobject this,
		jshortArray samples,
		jint offset,
		jdouble frequency
) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, ReceiverCalibration_nativePointer);
	jshort* buf = (*env)->GetShortArrayElements(env, samples, NULL);
	int len = (*env)->GetArrayLength(env, samples);
	int samplerate = ns->samplerate;

	int end;
	double phase;
	double complex acc;

	if (ns->remaining) {
		end = ns->remaining;
		offset = 0;
		phase = ns->phase;
		acc = ns->acc;
	}
	else {
		end = MIN(len, offset + ns->window_size);
		acc = 0;
		phase = 0;
	}

	for (int i = offset; i <= end; ++i) {
		phase += 2.0 * M_PI * frequency / samplerate;
		acc += buf[i] * (cos(phase) + I * sin(phase));
	}

	(*env)->ReleaseShortArrayElements(env, samples, buf, JNI_ABORT);

	if (len < offset + ns->window_size) {
		ns->remaining = offset + ns->window_size - len;
		ns->acc = acc;
		ns->phase = phase;
		
		return -1;
	}

	ns->remaining = 0;
	return sqr_magnitude(acc);
	
}


JNIEXPORT void JNICALL
Java_org_batnet_calib_ReceiverCalibration_finalize(JNIEnv* env, jobject this) {
	__android_log_print(ANDROID_LOG_DEBUG, "ReceiverCalibration", "finalise RECVCALIB");
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, ReceiverCalibration_nativePointer);
	free_native_buffers(ns);
	free(ns);
}
