#include <jni.h>
#include <tree-sitter-java.h>

#ifndef __ANDROID__
#define NATIVE_FUNCTION(name) JNIEXPORT jlong JNICALL name(JNIEnv * _env, jclass _class)
#else
#define NATIVE_FUNCTION(name) JNIEXPORT jlong JNICALL name()
#endif

NATIVE_FUNCTION(Java_io_github_treesitter_ktreesitter_java_TreeSitterJava_nativeLanguage) {
    return (jlong)tree_sitter_java();
}
