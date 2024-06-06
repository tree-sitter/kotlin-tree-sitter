#!/bin/bash -eu

cmake -S ktreesitter -B ktreesitter/.cmake/build \
      -DCMAKE_BUILD_TYPE=RelWithDebugInfo \
      -DCMAKE_VERBOSE_MAKEFILE=ON \
      -DCMAKE_OSX_ARCHITECTURES=arm64 \
      -DCMAKE_INSTALL_LIBDIR="$CMAKE_INSTALL_LIBDIR"

cmake --build ktreesitter/.cmake/build
cmake --install ktreesitter/.cmake/build --prefix ktreesitter/src/jvmMain/resources

for dir in languages/*/; do
    cmake -S "$dir" -B "${dir}.cmake/build" \
          -DCMAKE_BUILD_TYPE=RelWithDebugInfo \
          -DCMAKE_OSX_ARCHITECTURES=arm64 \
          -DCMAKE_INSTALL_LIBDIR="$CMAKE_INSTALL_LIBDIR"
    cmake --build "${dir}.cmake/build"
    cmake --install "${dir}.cmake/build" --prefix "${dir}src/jvmMain/resources"
done
