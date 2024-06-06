cmake_minimum_required(VERSION 3.12.0)

file(READ tree-sitter-java/Makefile MAKEFILE)

string(REGEX MATCH "VERSION := ([0-9.]+)" _ ${MAKEFILE})

project(ktreesitter-java VERSION ${CMAKE_MATCH_1})

find_package(JNI REQUIRED)

set(CMAKE_C_STANDARD 11)

if(MSVC)
    add_compile_options(/W3 /wd4244)
else(MSVC)
    set(CMAKE_C_VISIBILITY_PRESET hidden)
    add_compile_options(-Wall -Wextra
                        -Wno-unused-parameter
                        -Werror=implicit-function-declaration)
endif(MSVC)

include_directories(${JNI_INCLUDE_DIRS}
                    tree-sitter-java/bindings/c)

add_compile_definitions(TREE_SITTER_HIDE_SYMBOLS)

add_library(ktreesitter-java SHARED
            tree-sitter-java/src/parser.c
            # tree-sitter-java/src/scanner.c
            src/jni/binding.c)

set_target_properties(ktreesitter-java PROPERTIES DEFINE_SYMBOL "")

install(TARGETS ktreesitter-java ARCHIVE EXCLUDE_FROM_ALL)
