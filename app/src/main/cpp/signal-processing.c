#include <jni.h>
#include <complex.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdint.h>
#include <fftw3.h>
//
#ifndef JUNIT_BUILD_RANDOM
#include <sys/stat.h>
#include <unistd.h>
#endif

#include "android-log.h"

#ifndef JUNITTEST
//#define JUNITTEST
#endif

// vim:set path+=/home/zarandy/.android/ndk-bundle/sysroot/usr/include
// vim:let g:syntastic_c_include_dirs = ['/home/zarandy/.android/ndk-bundle/sysroot/usr/include/']

jclass PhaseShiftKeyingSignalProcessorClass;
jfieldID PhaseShiftKeyingSignalProcessor_carrierPhase;
jfieldID PhaseShiftKeyingSignalProcessor_isReceivingSignal;
jfieldID PhaseShiftKeyingSignalProcessor_nativePointer;
jfieldID PhaseShiftKeyingSignalProcessor_selectSymbols;
jfieldID PhaseShiftKeyingSignalProcessor_prevSymbolBoundary;
jfieldID PhaseShiftKeyingSignalProcessor_receivedEndOfPreamble;
jmethodID PhaseShiftKeyingSignalProcessor_startReceivingSignal;
jmethodID PhaseShiftKeyingSignalProcessor_set_frequency;

jclass ComplexClass;
jfieldID Complex_x;
jfieldID Complex_y;
jmethodID ComplexConstructor;

#define ENV(fun, args...) (*env)->fun(env, args)
#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

int _SAMPLERATE;
int _PREAMBLE_SIZE;
int _PREAMBLE_TRAILER_STRING_SIZE;
complex double _PREAMBLE_CONSTELLATION_POINTS[32];
complex double _PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS[32];

typedef struct {
	int receiver_buffer_size;
	jshort* buffer;
	int symbol_length;
	int symbol_window_size;
	int prev_symbol_boundary;
	double carrier_frequency;
	double complex* signal;
	double complex* s;
	double complex* sum;
	double* symbol_amplitudes;
	double complex* preamble_correlation;
	void* preamble_symbols_buffer; // 2D complex array with size _PREAMBLE_SIZE * receiver_buffer_size
#ifdef JUNITTEST
	FILE* rout;
	FILE* uout;
	FILE* sout;
	FILE* sumout;
	FILE* pout;
	FILE* tout;
	FILE* bout;
#endif
	fftw_plan fft_plan;
	double* fft_in_buffer;
	double complex* fft_out_buffer;
	int fft_buffer_len;
	double* frequencies;
} native_struct;

#ifdef JUNITTEST
void open_test_files(native_struct* ns) {
	ns->rout = fopen("sampledata/r_native.pcm", "w");
	ns->uout = fopen("sampledata/u_native.pcm", "w");
	ns->sout = fopen("sampledata/s_native.pcm", "w");
	ns->sumout = fopen("sampledata/sum_native.pcm", "w");
	ns->pout = fopen("sampledata/p_native.pcm", "w");
	ns->tout = fopen("sampledata/t_native.pcm", "w");
	ns->bout = fopen("sampledata/b_native.pcm", "w");
}

void close_test_files(native_struct* ns) {
	fclose(ns->rout);
	fclose(ns->uout);
	fclose(ns->sout);
	fclose(ns->sumout);
	fclose(ns->pout);
	fclose(ns->tout);
	fclose(ns->bout);
}
#endif

void allocate_buffers(native_struct* ns) {
	ns->buffer = malloc(ns->receiver_buffer_size * sizeof(jshort));
	ns->signal = malloc(ns->receiver_buffer_size * sizeof(double complex));
	double complex* s_buffer = malloc((ns->receiver_buffer_size + ns->symbol_length) * sizeof(double complex));
	ns->s = s_buffer + ns->symbol_length;
	double complex* sum_buffer = malloc((ns->receiver_buffer_size + ns->symbol_length) * sizeof(double complex));
	ns->sum = sum_buffer + ns->symbol_length;
	ns->symbol_amplitudes = malloc(ns->symbol_length * sizeof(double));
	ns->preamble_correlation = malloc(ns->receiver_buffer_size * sizeof(double complex));
	ns->preamble_symbols_buffer = malloc(_PREAMBLE_SIZE * ns->receiver_buffer_size * sizeof(double complex));
	int min_frequency_index = 18000.0 / _SAMPLERATE * ns->symbol_window_size;
	double* frequencies_buffer = malloc((ns->symbol_window_size / 2 - min_frequency_index) * sizeof(double));
	ns->frequencies = frequencies_buffer - min_frequency_index;
}

void clear_buffers(native_struct* ns) {
	memset(ns->s - ns->symbol_length, 0, ns->symbol_length * sizeof(double complex));
	memset(ns->sum - ns->symbol_length, 0, ns->symbol_length * sizeof(double complex));
	memset(ns->symbol_amplitudes, 0, ns->symbol_length * sizeof(double));
	memset(ns->preamble_correlation, 0, ns->receiver_buffer_size * sizeof(double complex));
	memset(ns->preamble_symbols_buffer, 0, _PREAMBLE_SIZE * ns->receiver_buffer_size * sizeof(double complex));
	int min_frequency_index = 18000.0 / _SAMPLERATE * ns->symbol_window_size;
	memset(ns->frequencies + min_frequency_index, 0, (ns->symbol_window_size / 2 - min_frequency_index) * sizeof(double));
}

void free_buffers(native_struct* ns) {
	free(ns->buffer);
	free(ns->signal);
	free(ns->s - ns->symbol_length);
	free(ns->sum - ns->symbol_length);
	free(ns->symbol_amplitudes);
	free(ns->preamble_correlation);
	free(ns->preamble_symbols_buffer);
	int min_frequency_index = 18000.0 / _SAMPLERATE * ns->symbol_window_size;
	free(ns->frequencies + min_frequency_index);
}

void allocate_fft_plan_and_buffers(native_struct* ns) {
	ns->fft_buffer_len = ns->receiver_buffer_size / ns->symbol_length * ns->symbol_window_size;
	ns->fft_in_buffer = fftw_malloc(ns->fft_buffer_len * sizeof(double));
	ns->fft_out_buffer = fftw_malloc((ns->fft_buffer_len / 2 + 1) * sizeof(double complex));
	ns->fft_plan = fftw_plan_dft_r2c_1d(ns->fft_buffer_len, ns->fft_in_buffer, ns->fft_out_buffer, FFTW_ESTIMATE);
}

void free_fft_plan_and_buffers(native_struct* ns) {
	fftw_free(ns->fft_plan);
	fftw_free(ns->fft_in_buffer);
	fftw_free(ns->fft_out_buffer);
}

JNIEXPORT jlong JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeInit(
		JNIEnv* env, 
		jobject this, 
		jint SAMPLERATE,
		jdouble MID_FREQUENCY,
		jint SAMPLES_PER_SYMBOL,
		jint WINDOW_SIZE,
		jint PREAMBLE_SIZE,
		jint PREAMBLE_TRAILER_STRING_SIZE,
		jdoubleArray PREAMBLE_CONSTELLATION_POINTS,
		jdoubleArray PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS
) {
	_SAMPLERATE = SAMPLERATE;
	_PREAMBLE_SIZE = PREAMBLE_SIZE;
	_PREAMBLE_TRAILER_STRING_SIZE = PREAMBLE_TRAILER_STRING_SIZE;

#ifdef JUNIT_BUILD_RANDOM
	__android_log_print(ANDROID_LOG_DEBUG, "Build Random", "%lld", JUNIT_BUILD_RANDOM);
#endif
	__android_log_print(ANDROID_LOG_DEBUG, "PSK", "INIT SIGNALPROCESSING");

	PhaseShiftKeyingSignalProcessorClass = (*env)->GetObjectClass(env, this);
	PhaseShiftKeyingSignalProcessor_carrierPhase = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "carrierPhase", "Lorg/batnet/utils/Complex;");
	PhaseShiftKeyingSignalProcessor_isReceivingSignal = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "isReceivingSignal", "Z");
	PhaseShiftKeyingSignalProcessor_selectSymbols = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "selectSymbols", "[Lorg/batnet/utils/Complex;");
	PhaseShiftKeyingSignalProcessor_prevSymbolBoundary = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "prevSymbolBoundary", "I");
	PhaseShiftKeyingSignalProcessor_receivedEndOfPreamble = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "receivedEndOfPreamble", "I");
	PhaseShiftKeyingSignalProcessor_startReceivingSignal = (*env)->GetMethodID(env, PhaseShiftKeyingSignalProcessorClass, "startReceivingSignal", "(DLorg/batnet/utils/Complex;I)V");
	PhaseShiftKeyingSignalProcessor_set_frequency = (*env)->GetMethodID(env, PhaseShiftKeyingSignalProcessorClass, "setCarrierFrequency", "(D)V");

	PhaseShiftKeyingSignalProcessor_nativePointer = (*env)->GetFieldID(env, PhaseShiftKeyingSignalProcessorClass, "nativePointer", "J");

	native_struct* ns = malloc(sizeof(native_struct));
	ns->receiver_buffer_size = SAMPLES_PER_SYMBOL * PREAMBLE_SIZE;
	ns->prev_symbol_boundary = 0;
	ns->carrier_frequency = MID_FREQUENCY;
	ns->symbol_length = SAMPLES_PER_SYMBOL;
	ns->symbol_window_size = WINDOW_SIZE;

	allocate_buffers(ns);
	clear_buffers(ns);
	allocate_fft_plan_and_buffers(ns);

	ComplexClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "org/batnet/utils/Complex"));
	Complex_x = (*env)->GetFieldID(env, ComplexClass, "x", "D");
	Complex_y = (*env)->GetFieldID(env, ComplexClass, "y", "D");
	ComplexConstructor = (*env)->GetMethodID(env, ComplexClass, "<init>", "(DD)V");

#ifdef JUNITTEST
#ifndef JUNIT_BUILD_RANDOM
	chdir("/sdcard");
	mkdir("sampledata", 0777);
#endif
	open_test_files(ns);
#endif

	jdouble* preamble;
	preamble = (*env)->GetDoubleArrayElements(env, PREAMBLE_CONSTELLATION_POINTS, NULL);
	memcpy(_PREAMBLE_CONSTELLATION_POINTS, preamble, _PREAMBLE_SIZE * sizeof(double complex));
	(*env)->ReleaseDoubleArrayElements(env, PREAMBLE_CONSTELLATION_POINTS, preamble, JNI_ABORT);

	preamble = (*env)->GetDoubleArrayElements(env, PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS, NULL);
	memcpy(_PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS, preamble, _PREAMBLE_TRAILER_STRING_SIZE * sizeof(double complex));
	(*env)->ReleaseDoubleArrayElements(env, PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS, preamble, JNI_ABORT);

	__android_log_print(ANDROID_LOG_DEBUG, "PSK", "INIT SIGNALPROCESSING DONE");

	return (jlong) ns;
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeClose(JNIEnv* env, jobject this) {
#ifdef JUNITTEST
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	close_test_files(ns);
#endif
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeStartRecording(JNIEnv* env, jobject this) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	clear_buffers(ns);
#ifdef JUNITTEST
	open_test_files(ns);
#endif
}


JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativePauseRecording(JNIEnv* env, jobject this) {
#ifdef JUNITTEST
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	close_test_files(ns);
#endif
}

double complex unwrapKtComplex(JNIEnv* env, jobject c) {
	double x = (*env)->GetDoubleField(env, c, Complex_x);
	double y = (*env)->GetDoubleField(env, c, Complex_y);
	return x+y*I;
}

jobject wrapKtComplex(JNIEnv* env, double complex c) {
	return (*env)->NewObject(env, ComplexClass, ComplexConstructor, creal(c), cimag(c));
}


// this is part of the C standard library (called cabs) but are missing from android for some reason
double sqr_magnitude(double complex z) {
	return creal(z) * creal(z) + cimag(z) * cimag(z);
}

double magnitude(double complex z) {
	return sqrt(creal(z) * creal(z) + cimag(z) * cimag(z));
}

double complex normalised(double complex z) {
	return z / magnitude(z);
}

void startReceivingSignal(JNIEnv* env, jobject this, double signal_strength, double complex carrier_phase, int offset) {
	(*env)->CallVoidMethod(env, this, PhaseShiftKeyingSignalProcessor_startReceivingSignal,
			signal_strength, wrapKtComplex(env, carrier_phase), offset);
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeChangeSymbolLength(JNIEnv* env, jobject this, jint newSymbolLength, jint newWindowSize);

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_retrieveSymbolsInC(JNIEnv* env, jobject this, jshortArray buffer) {


	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	native_struct nsl = *ns;

	jshort* buf = nsl.buffer;//(*env)->GetShortArrayElements(env, buffer, NULL);
	(*env)->GetShortArrayRegion(env, buffer, 0, nsl.receiver_buffer_size, buf);

	jboolean isReceivingSignal = (*env)->GetBooleanField(env, this, PhaseShiftKeyingSignalProcessor_isReceivingSignal);

	if (!isReceivingSignal) {
		for (int i = 0; i < nsl.fft_buffer_len; ++i) {
			nsl.fft_in_buffer[i] = buf[i + nsl.receiver_buffer_size - nsl.fft_buffer_len];
		}
		fftw_execute(nsl.fft_plan);
		int min_frequency_index = 18000.0 / _SAMPLERATE * nsl.symbol_window_size;
		int best_index = nsl.carrier_frequency / _SAMPLERATE * nsl.symbol_window_size;
		for (int f = min_frequency_index; f < nsl.symbol_window_size / 2; ++f) {
			nsl.frequencies[f] *= 0.8;
		}
		for (int f = min_frequency_index; f < nsl.symbol_window_size / 2; ++f) {
			nsl.frequencies[f] += sqr_magnitude(nsl.fft_out_buffer[f * nsl.receiver_buffer_size / nsl.symbol_length]);
			if (nsl.frequencies[best_index] < nsl.frequencies[f]) {
				best_index = f;
			}
		}
		double best_frequency = ((double) best_index) * _SAMPLERATE / nsl.symbol_window_size;
		if (best_frequency != nsl.carrier_frequency) {
			ns->carrier_frequency = best_frequency;
			nsl.carrier_frequency = best_frequency;
			//__android_log_print(ANDROID_LOG_DEBUG, "PSK", "change carrier frequency to %f", nsl.carrier_frequency);
			(*env)->CallVoidMethod(env, this, PhaseShiftKeyingSignalProcessor_set_frequency, nsl.carrier_frequency);
		}
	}

	double OMEGA = 2.0 * M_PI * nsl.carrier_frequency / _SAMPLERATE;
	double complex carrier_phase = unwrapKtComplex(env, (*env)->GetObjectField(env, this, PhaseShiftKeyingSignalProcessor_carrierPhase));
	if (isnan(creal(carrier_phase)) || isnan(cimag(carrier_phase))) { __android_log_print(ANDROID_LOG_ERROR, "PSK", "carrier_phase became NaN: A"); }


	for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
		nsl.signal[i] = (cos(OMEGA * i) * buf[i]) + I*(sin(OMEGA * i) * buf[i]);
	}

	//(*env)->ReleaseShortArrayElements(env, buffer, buf, JNI_ABORT);

	for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
		nsl.s[i] = nsl.signal[i] * carrier_phase;
		nsl.sum[i] = nsl.sum[i-1] + nsl.s[i] - nsl.s[i - nsl.symbol_window_size];

#ifdef JUNITTEST
#endif
	}

	int signal_start_offset = -1; // set when signal starts

	int symbolBoundary = nsl.prev_symbol_boundary;

	if (!isReceivingSignal) {
		double complex (*preamble_symbols)[nsl.receiver_buffer_size] = nsl.preamble_symbols_buffer;
		int m = 0;
		for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
			int j = _PREAMBLE_SIZE-1;
			int k = i;
			while (k >= 0 && j >= 0) {
				double complex symbol = nsl.sum[k] / _PREAMBLE_CONSTELLATION_POINTS[j];
				preamble_symbols[j][i] = symbol;
				nsl.preamble_correlation[i] += symbol;
				--j;
				k -= nsl.symbol_length;
			}
			if (sqr_magnitude(nsl.preamble_correlation[i]) > sqr_magnitude(nsl.preamble_correlation[m])) {
				m = i;
			}

#ifdef JUNITTEST
			double complex f = nsl.preamble_correlation[i] / 1048576.0;
			fwrite(&f, sizeof(double complex), 1, nsl.pout);
#endif

		}
#ifdef JUNITTEST
		for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
			double f = sqr_magnitude(nsl.preamble_correlation[i]) / 10000.0;
			fwrite(&f, sizeof(double), 1, nsl.tout);
		}
#endif
		double complex carrier_phase_rotation; // initialised if preamble is found
		int preamble_found = 0;
		if (sqr_magnitude(nsl.preamble_correlation[m]) >= 10000.0) { // magic number
			preamble_found = 1;
			carrier_phase_rotation = normalised(nsl.preamble_correlation[m]);
			for (int j = 0; j < _PREAMBLE_SIZE; ++j) {
				double complex symbol_rotated = preamble_symbols[j][m] / carrier_phase_rotation;
				if (creal(symbol_rotated) < fabs(cimag(symbol_rotated))) {
					preamble_found = 0;
					break;
				}
			}
		}
		if (preamble_found) {	
			__android_log_print(ANDROID_LOG_DEBUG, "PSK", "Maximum preamble correlation: %f", sqr_magnitude(nsl.preamble_correlation[m]) * nsl.receiver_buffer_size);
			carrier_phase /= carrier_phase_rotation;
			startReceivingSignal(env, this, sqr_magnitude(nsl.preamble_correlation[m]) / _PREAMBLE_SIZE / _PREAMBLE_SIZE / 100, carrier_phase, m);
			symbolBoundary = m % nsl.symbol_length;
			int i = MAX(0, MIN(m - nsl.symbol_length / 2, nsl.receiver_buffer_size - nsl.symbol_length));
			for (int j = i; j < i + nsl.symbol_length; ++j) {
				nsl.symbol_amplitudes[j % nsl.symbol_length] = sqr_magnitude(nsl.preamble_correlation[j]);
			}
			signal_start_offset = m;
			for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
				nsl.s[i] /= carrier_phase_rotation;
				nsl.sum[i] /= carrier_phase_rotation;
			}
			for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
				nsl.preamble_correlation[i] = 0.0;
			}
		}
		else {
			for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
				nsl.preamble_correlation[i] = 0.0;
				int j = 0;
				int k = i - (_PREAMBLE_SIZE-1) * nsl.symbol_length + nsl.receiver_buffer_size;
				while (k < nsl.receiver_buffer_size && j < _PREAMBLE_SIZE) {
					double complex symbol = nsl.sum[k] / _PREAMBLE_CONSTELLATION_POINTS[j];
					preamble_symbols[j][i] = symbol;
					nsl.preamble_correlation[i] += symbol;
					++j;
					k += nsl.symbol_length;
				}
			}
		}
	}
	else { // if isReceivingSignal
		int receivedEndOfPreamble = (*env)->GetIntField(env, this, PhaseShiftKeyingSignalProcessor_receivedEndOfPreamble);
		if (receivedEndOfPreamble < _PREAMBLE_TRAILER_STRING_SIZE) {
			double complex carrier_phase_rotation = 1.0;
			int number_of_found_preamble_points = 0;
			for (int k = nsl.prev_symbol_boundary - nsl.symbol_length/2; k < nsl.prev_symbol_boundary + nsl.symbol_length/2; ++k) {
				int j = (k + nsl.symbol_length) % nsl.symbol_length;
				nsl.symbol_amplitudes[j] *= 2.0;
				double complex acc = 0.0;
				int i;
				for (i = 0; i * nsl.symbol_length + k < nsl.receiver_buffer_size && i < _PREAMBLE_TRAILER_STRING_SIZE - receivedEndOfPreamble; ++i) {
					double complex here = nsl.sum[i * nsl.symbol_length + k];
					double complex constellation_point = _PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS[receivedEndOfPreamble + i];
					acc += here / constellation_point;
				}
				nsl.symbol_amplitudes[j] += (creal(acc) > 0.0) ? 1.1*creal(acc)*creal(acc) + cimag(acc)*cimag(acc) : 0.0;
				if (nsl.symbol_amplitudes[j] > nsl.symbol_amplitudes[symbolBoundary]) {
					symbolBoundary = j;
					carrier_phase_rotation = acc;
					number_of_found_preamble_points = i;
				}
			}
			carrier_phase_rotation = normalised(1.0 * (nsl.receiver_buffer_size / nsl.symbol_length - number_of_found_preamble_points) + 1.0 / normalised(carrier_phase_rotation) * number_of_found_preamble_points);
			carrier_phase *= carrier_phase_rotation;
			for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
				nsl.s[i] *= carrier_phase_rotation;
				nsl.sum[i] *= carrier_phase_rotation;
			}
		}
#ifdef JUNITTEST
		for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
			double complex f = 0.0;
			fwrite(&f, sizeof(double complex), 1, nsl.pout);
			fwrite(&f, sizeof(double), 1, nsl.tout);
		}
#endif
	}

#ifdef JUNITTEST
	for (int i = 0; i < nsl.receiver_buffer_size; ++i) {
		double complex f;
		f = nsl.signal[i] / 256.0;
		fwrite(&f, sizeof(double complex), 1, nsl.rout);
		f = nsl.s[i] / 256.0;
		fwrite(&f, sizeof(double complex), 1, nsl.sout);
		f = nsl.sum[i] / 131072.0;
		fwrite(&f, sizeof(double complex), 1, nsl.sumout);
	}
	for (int i = 0; i < nsl.symbol_length; ++i) {
		double f;
		f = nsl.symbol_amplitudes[i] / 17179869184.0;
		fwrite(&f, sizeof(double), 1, nsl.bout);
	}
	double zero = 0.0;
	for (int i = nsl.symbol_length; i < nsl.receiver_buffer_size; ++i) {
		fwrite(&zero, sizeof(double), 1, nsl.bout);
	}
#endif


	int skipFirstSample = 
		(!isReceivingSignal) ? (
				(signal_start_offset == -1) ? nsl.receiver_buffer_size / nsl.symbol_length
				: (signal_start_offset / nsl.symbol_length + 1)
		) :
		(nsl.prev_symbol_boundary > nsl.symbol_length * 3 / 4 && symbolBoundary < nsl.symbol_length * 1 / 4) ? 1 :
		(nsl.prev_symbol_boundary < nsl.symbol_length * 1 / 4 && symbolBoundary > nsl.symbol_length * 3 / 4) ? -1
		: 0;

	ns->prev_symbol_boundary = symbolBoundary;
	nsl.prev_symbol_boundary = symbolBoundary;
	(*env)->SetIntField(env, this, PhaseShiftKeyingSignalProcessor_prevSymbolBoundary, symbolBoundary);

	jobjectArray selectSymbols = (*env)->GetObjectField(env, this, PhaseShiftKeyingSignalProcessor_selectSymbols);
	for (int i = 0; i <= skipFirstSample; ++i) {
		(*env)->SetObjectArrayElement(env, selectSymbols, i, NULL);
	}
	for (int i = skipFirstSample; i < nsl.receiver_buffer_size / nsl.symbol_length; ++i) {
		(*env)->SetObjectArrayElement(env, selectSymbols, i+1, 
				wrapKtComplex(env, nsl.sum[MAX(i * nsl.symbol_length + symbolBoundary, 0)]));
	}
	(*env)->SetObjectField(env, this, PhaseShiftKeyingSignalProcessor_carrierPhase, wrapKtComplex(env, carrier_phase));

	memcpy(nsl.s - nsl.symbol_length, nsl.s + nsl.receiver_buffer_size - nsl.symbol_length, nsl.symbol_length * sizeof(double complex));
	memcpy(nsl.sum - nsl.symbol_length, nsl.sum + nsl.receiver_buffer_size - nsl.symbol_length, nsl.symbol_length * sizeof(double complex));
	

#ifdef JUNITTEST
	fflush(nsl.rout);
	fflush(nsl.uout);
	fflush(nsl.sout);
	fflush(nsl.sumout);
	fflush(nsl.pout);
	fflush(nsl.tout);
	fflush(nsl.bout);
#endif
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeSetCarrierFrequency(JNIEnv* env, jobject this, double value) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	ns->carrier_frequency = value;
	//__android_log_print(ANDROID_LOG_DEBUG, "native set carrier frequency", "%f", value);
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_nativeChangeSymbolLength(JNIEnv* env, jobject this, jint newSymbolLength, jint newWindowSize) {
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	if (ns->symbol_length == newSymbolLength && ns->symbol_window_size == newWindowSize) { return; }
	__android_log_print(ANDROID_LOG_DEBUG, "native set symbol length", "symbol length current: %d, new: %d, window size current: %d new: %d, buffer size current: %d, new: %d", ns->symbol_length, newSymbolLength, ns->symbol_window_size, newWindowSize, ns->receiver_buffer_size, newSymbolLength * _PREAMBLE_SIZE);

	free_buffers(ns);
	free_fft_plan_and_buffers(ns);

	ns->symbol_length = newSymbolLength;
	ns->symbol_window_size = newWindowSize;
	ns->receiver_buffer_size = newSymbolLength * _PREAMBLE_SIZE;

	allocate_buffers(ns);
	clear_buffers(ns);
	allocate_fft_plan_and_buffers(ns);
}

JNIEXPORT void JNICALL
Java_org_batnet_receiver_PhaseShiftKeyingSignalProcessor_finalize(JNIEnv* env, jobject this) {
	__android_log_print(ANDROID_LOG_DEBUG, "PSK", "finalise SIGNALPROCESSING");
	native_struct* ns = (native_struct*) (*env)->GetLongField(env, this, PhaseShiftKeyingSignalProcessor_nativePointer);
	free_buffers(ns);
	free_fft_plan_and_buffers(ns);
	free(ns);
}
