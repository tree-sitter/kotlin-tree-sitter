cmake_minimum_required(VERSION 3.25)

file(READ ../gradle.properties GRADLE_PROPERTIES)
string(REGEX MATCH "project[.]version=([0-9.]+)" _ ${GRADLE_PROPERTIES})

project(ktreesitter VERSION ${CMAKE_MATCH_1} LANGUAGES C)

find_package(JNI REQUIRED)

block()
    set(BUILD_SHARED_LIBS OFF)
    set(AMALGAMATED ON)
    add_subdirectory(../tree-sitter tree-sitter EXCLUDE_FROM_ALL)
endblock()

target_compile_definitions(tree-sitter PUBLIC TREE_SITTER_HIDE_SYMBOLS)

set(KTREESITTER_SOURCES language.c lookahead_iterator.c node.c parser.c
                        query.c query_cursor.c tree.c tree_cursor.c module.c)
list(TRANSFORM KTREESITTER_SOURCES PREPEND src/jni/)
add_library(ktreesitter SHARED ${KTREESITTER_SOURCES})

if(MSVC)
    target_compile_options(ktreesitter PRIVATE /W3 /wd4244 /we4022 /we4013)
else()
    target_compile_options(ktreesitter PRIVATE -Wall -Wextra
                           -Wno-unused-parameter
                           -Werror=incompatible-pointer-types
                           -Werror=implicit-function-declaration)
endif()

target_link_libraries(ktreesitter PRIVATE JNI::JNI tree-sitter)

set_target_properties(ktreesitter PROPERTIES C_STANDARD 11
                                             C_VISIBILITY_PRESET hidden
                                             DEFINE_SYMBOL "")

install(TARGETS ktreesitter ARCHIVE EXCLUDE_FROM_ALL)
