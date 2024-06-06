#!/usr/bin/env pwsh

& cmake -S ktreesitter -B ktreesitter/.cmake/build `
        -DCMAKE_VERBOSE_MAKEFILE=ON `
        -DCMAKE_INSTALL_BINDIR="$env:CMAKE_INSTALL_LIBDIR"
& cmake --build ktreesitter/.cmake/build --config Debug
& cmake --install ktreesitter/.cmake/build --config Debug --prefix ktreesitter/src/jvmMain/resources

foreach ($dir in Get-ChildItem -Directory -Path languages) {
    & cmake -S "$dir" -B "$dir/.cmake/build" `
            -DCMAKE_INSTALL_BINDIR="$env:CMAKE_INSTALL_LIBDIR"
    & cmake --build "$dir/.cmake/build" --config Debug
    & cmake --install "$dir/.cmake/build" --config Debug --prefix "$dir/src/jvmMain/resources"
}
