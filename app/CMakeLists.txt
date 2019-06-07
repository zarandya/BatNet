# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(signal-processing SHARED src/main/cpp/signal-processing)

add_library(receiver-calibration SHARED src/main/cpp/receiver-calibration)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

find_library(m-lib m)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

#ADD_LIBRARY(fftw3 STATIC IMPORTED)
#SET_TARGET_PROPERTIES(fftw3 PROPERTIES
#    IMPORTED_LOCATION /usr/lib/libboost_unit_test_framework.a)
#TARGET_LINK_LIBRARIES(calib fftw3)

# FFTW
set(fftw_src_DIR libs/fftw-3.3.8)
add_subdirectory(${fftw_src_DIR})
add_library( fftw SHARED IMPORTED )
set_target_properties( fftw PROPERTIES IMPORTED_LOCATION ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libfftw3.so )
#find_library(fftw fftw3 ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})
include_directories( ${fftw_src_DIR}/api )


target_link_libraries(receiver-calibration ${log-lib} ${m-lib} fftw)
target_link_libraries(signal-processing ${log-lib} ${m-lib} fftw)
