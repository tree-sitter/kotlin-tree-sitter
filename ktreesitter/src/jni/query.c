#include <ctype.h>
#include <string.h>

#include "utils.h"

static inline bool is_valid_identifier_char(char ch) { return isalnum(ch) || ch == '_'; }

static inline bool is_valid_predicate_char(char ch) {
    return isalnum(ch) || ch == '-' || ch == '_' || ch == '?' || ch == '.' || ch == '!';
}

jlong query_init(JNIEnv *env, jclass _class, jlong language, jstring source) {
    TSQueryError error_type;
    const char *source_chars = (*env)->GetStringUTFChars(env, source, NULL);
    uint32_t error_offset, source_len = (*env)->GetStringUTFLength(env, source);
    TSQuery *self =
        ts_query_new((TSLanguage *)language, source_chars, source_len, &error_offset, &error_type);
    if (self != NULL) {
        (*env)->ReleaseStringUTFChars(env, source, source_chars);
        return (jlong)self;
    }

    uint32_t start = 0, end = 0, row = 0, column;
#ifndef _MSC_VER
    char *line = strtok((char *)source_chars, "\n");
#else
    char *next_token = NULL;
    char *line = strtok_s((char *)source_chars, "\n", &next_token);
#endif
    while (line != NULL) {
        end = start + (uint32_t)strlen(line) + 1;
        if (end > error_offset)
            break;
        start = end;
        row += 1;
#ifndef _MSC_VER
        line = strtok(NULL, "\n");
#else
        line = strtok_s(NULL, "\n", &next_token);
#endif
    }
    column = error_offset - start, end = 0;

    switch (error_type) {
        case TSQueryErrorSyntax: {
            jobject exception;
            if (error_offset < source_len) {
                exception = NEW_OBJECT(QueryError$Syntax, (jlong)row, (jlong)column);
            } else {
                exception = NEW_OBJECT(QueryError$Syntax, (jlong)-1, (jlong)-1);
            }
            (*env)->Throw(env, (jthrowable)exception);
            break;
        }
        case TSQueryErrorCapture: {
            while (is_valid_predicate_char(source_chars[error_offset + end])) {
                end += 1;
            }

            char *capture_chars = calloc(end + 1, sizeof(char));
            memcpy(capture_chars, &source_chars[error_offset], end);
            jstring capture = (*env)->NewStringUTF(env, capture_chars);
            jobject exception = NEW_OBJECT(QueryError$Capture, (jint)row, (jint)column, capture);
            (*env)->Throw(env, (jthrowable)exception);
            free(capture_chars);
            break;
        }
        case TSQueryErrorNodeType: {
            while (is_valid_identifier_char(source_chars[error_offset + end])) {
                end += 1;
            }

            char *node_chars = calloc(end + 1, sizeof(char));
            memcpy(node_chars, &source_chars[error_offset], end);
            jstring node = (*env)->NewStringUTF(env, node_chars);
            jobject exception = NEW_OBJECT(QueryError$NodeType, (jint)row, (jint)column, node);
            (*env)->Throw(env, (jthrowable)exception);
            free(node_chars);
            break;
        }
        case TSQueryErrorField: {
            while (is_valid_identifier_char(source_chars[error_offset + end])) {
                end += 1;
            }

            char *field_chars = calloc(end + 1, sizeof(char));
            memcpy(field_chars, &source_chars[error_offset], end);
            jstring field = (*env)->NewStringUTF(env, field_chars);
            jobject exception = NEW_OBJECT(QueryError$Field, (jint)row, (jint)column, field);
            (*env)->Throw(env, (jthrowable)exception);
            free(field_chars);
            break;
        }
        case TSQueryErrorStructure: {
            jobject exception = NEW_OBJECT(QueryError$Structure, (jint)row, (jint)column);
            (*env)->Throw(env, (jthrowable)exception);
            break;
        }
        default:
            UNREACHABLE();
    }

    (*env)->ReleaseStringUTFChars(env, source, source_chars);
    return -1;
}

jlong query_cursor CRITICAL_NO_ARGS() { return (jlong)ts_query_cursor_new(); }

void query_delete CRITICAL_ARGS(jlong query, jlong cursor) {
    ts_query_delete((TSQuery *)query);
    ts_query_cursor_delete((TSQueryCursor *)cursor);
}

jint query_get_pattern_count(JNIEnv *env, jobject this) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    return (jint)ts_query_pattern_count(self);
}

jint query_get_capture_count(JNIEnv *env, jobject this) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    return (jint)ts_query_capture_count(self);
}

jlong query_get_timeout_micros(JNIEnv *env, jobject this) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    return (jlong)ts_query_cursor_timeout_micros(cursor);
}

void query_set_timeout_micros(JNIEnv *env, jobject this, jlong value) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    ts_query_cursor_set_timeout_micros(cursor, (uint64_t)value);
    (*env)->SetLongField(env, this, global_field_cache.Query_timeoutMicros, value);
}

jint query_get_match_limit(JNIEnv *env, jobject this) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    return (jint)ts_query_cursor_match_limit(cursor);
}

void query_set_match_limit(JNIEnv *env, jobject this, jint value) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    ts_query_cursor_set_match_limit(cursor, (uint32_t)value);
    (*env)->SetIntField(env, this, global_field_cache.Query_matchLimit, value);
}

void query_set_max_start_depth(JNIEnv *env, jobject this, jint value) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    ts_query_cursor_set_max_start_depth(cursor, (uint32_t)value);
    (*env)->SetIntField(env, this, global_field_cache.Query_maxStartDepth, value);
}

void query_native_set_byte_range(JNIEnv *env, jobject this, jint start, jint end) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    ts_query_cursor_set_byte_range(cursor, (uint32_t)start, (uint32_t)end);
}

void query_native_set_point_range(JNIEnv *env, jobject this, jobject start, jobject end) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    TSPoint start_point = unmarshal_point(env, start), end_point = unmarshal_point(env, end);
    ts_query_cursor_set_point_range(cursor, start_point, end_point);
}

jboolean query_did_exceed_match_limit(JNIEnv *env, jobject this) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    return (jboolean)ts_query_cursor_did_exceed_match_limit(cursor);
}

void query_disable_pattern(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    if (ts_query_pattern_count(self) > (uint32_t)index) {
        ts_query_disable_pattern(self, (uint32_t)index);
    } else {
        const char *fmt = "Pattern index %u is out of bounds";
        char buffer[45] = {0};
        sprintf_s(buffer, 45, fmt, (uint32_t)index);
        THROW(IndexOutOfBoundsException, (const char *)buffer);
    }
}

void query_native_disable_capture(JNIEnv *env, jobject this, jstring capture) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    const char *capture_chars = (*env)->GetStringUTFChars(env, capture, NULL);
    uint32_t length = (uint32_t)(*env)->GetStringUTFLength(env, capture);
    ts_query_disable_capture(self, capture_chars, length);
    (*env)->ReleaseStringUTFChars(env, capture, capture_chars);
}

jint query_start_byte_for_pattern(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    if (ts_query_pattern_count(self) > (uint32_t)index) {
        return (jint)ts_query_start_byte_for_pattern(self, (uint32_t)index);
    }

    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf_s(buffer, 45, fmt, (uint32_t)index);
    THROW(IndexOutOfBoundsException, (const char *)buffer);
    return -1;
}

jint query_end_byte_for_pattern(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    if (ts_query_pattern_count(self) > (uint32_t)index) {
        return (jint)ts_query_end_byte_for_pattern(self, (uint32_t)index);
    }

    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf_s(buffer, 45, fmt, (uint32_t)index);
    THROW(IndexOutOfBoundsException, (const char *)buffer);
    return -1;
}

jboolean query_is_pattern_rooted(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    if (ts_query_pattern_count(self) > (uint32_t)index) {
        return (jboolean)ts_query_is_pattern_rooted(self, (uint32_t)index);
    }

    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf_s(buffer, 45, fmt, (uint32_t)index);
    THROW(IndexOutOfBoundsException, (const char *)buffer);
    return JNI_FALSE;
}

jboolean query_is_pattern_non_local(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    if (ts_query_pattern_count(self) > (uint32_t)index) {
        return (jboolean)ts_query_is_pattern_non_local(self, (uint32_t)index);
    }

    const char *fmt = "Pattern index %u is out of bounds";
    char buffer[45] = {0};
    sprintf_s(buffer, 45, fmt, (uint32_t)index);
    THROW(IndexOutOfBoundsException, (const char *)buffer);
    return JNI_FALSE;
}

jboolean query_native_is_pattern_guaranteed_at_step(JNIEnv *env, jobject this, jint offset) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    return (jboolean)ts_query_is_pattern_guaranteed_at_step(self, (uint32_t)offset);
}

jint query_string_count(JNIEnv *env, jobject this) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    return (jint)ts_query_string_count(self);
}

void query_exec(JNIEnv *env, jobject this, jobject node) {
    TSQuery *query = GET_POINTER(TSQuery, this, Query_self);
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    TSNode ts_node = unmarshal_node(env, node);
    ts_query_cursor_exec(cursor, query, ts_node);
}

jstring query_capture_name_for_id(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    uint32_t length;
    const char *name = ts_query_capture_name_for_id(self, (uint32_t)index, &length);
    return name ? (*env)->NewStringUTF(env, name) : NULL;
}

jstring query_string_value_for_id(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    uint32_t length;
    const char *value = ts_query_string_value_for_id(self, (uint32_t)index, &length);
    return value ? (*env)->NewStringUTF(env, value) : NULL;
}

jobject query_predicates_for_pattern(JNIEnv *env, jobject this, jint index) {
    TSQuery *self = GET_POINTER(TSQuery, this, Query_self);
    uint32_t step_count;
    const TSQueryPredicateStep *steps =
        ts_query_predicates_for_pattern(self, (uint32_t)index, &step_count);
    if (step_count == 0)
        return NULL;

    jobject predicates = NEW_OBJECT(ArrayList, (jint)step_count);
    for (uint32_t i = 0; i < step_count; ++i) {
        const jint values[2] = {(jint)steps[i].value_id, (jint)steps[i].type};
        jintArray predicate = (jintArray)(*env)->NewIntArray(env, 2);
        (*env)->SetIntArrayRegion(env, predicate, 0, 2, values);
        CALL_METHOD(Boolean, predicates, ArrayList_add, predicate);
        (*env)->DeleteLocalRef(env, predicate);
    }
    return predicates;
}

jobject query_next_match(JNIEnv *env, jobject this, jobject tree) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    TSQueryMatch match;
    if (!ts_query_cursor_next_match(cursor, &match))
        return NULL;

    jobject capture_names = GET_FIELD(Object, this, Query_captureNames);
    jobject captures = NEW_OBJECT(ArrayList, (jint)match.capture_count);
    for (uint16_t i = 0; i < match.capture_count; ++i) {
        TSQueryCapture capture = match.captures[i];
        jobject node = marshal_node(env, capture.node, tree);
        jobject name = CALL_METHOD(Object, capture_names, List_get, capture.index);
        if ((*env)->ExceptionCheck(env))
            return NULL;

        jobject capture_obj = NEW_OBJECT(QueryCapture, node, name);
        CALL_METHOD(Boolean, captures, ArrayList_add, capture_obj);
        (*env)->DeleteLocalRef(env, capture_obj);
        (*env)->DeleteLocalRef(env, node);
        (*env)->DeleteLocalRef(env, name);
        if ((*env)->ExceptionCheck(env))
            return NULL;
    }
    return NEW_OBJECT(QueryMatch, (jint)match.pattern_index, captures);
}

jobject query_next_capture(JNIEnv *env, jobject this, jobject tree) {
    TSQueryCursor *cursor = GET_POINTER(TSQueryCursor, this, Query_cursor);
    uint32_t capture_index;
    TSQueryMatch match;
    if (!ts_query_cursor_next_capture(cursor, &match, &capture_index))
        return NULL;

    jobject capture_names = GET_FIELD(Object, this, Query_captureNames);
    jobject captures = NEW_OBJECT(ArrayList, (jint)match.capture_count);
    for (uint16_t i = 0; i < match.capture_count; ++i) {
        TSQueryCapture capture = match.captures[i];
        jobject node = marshal_node(env, capture.node, tree);
        jobject name = CALL_METHOD(Object, capture_names, List_get, capture.index);
        if ((*env)->ExceptionCheck(env))
            return NULL;

        jobject capture_obj = NEW_OBJECT(QueryCapture, node, name);
        CALL_METHOD(Boolean, captures, ArrayList_add, capture_obj);
        (*env)->DeleteLocalRef(env, capture_obj);
        (*env)->DeleteLocalRef(env, node);
        (*env)->DeleteLocalRef(env, name);
        if ((*env)->ExceptionCheck(env))
            return NULL;
    }
    jobject match_obj = NEW_OBJECT(QueryMatch, (jint)match.pattern_index, captures);
    jobject index = (*env)->AllocObject(env, global_class_cache.UInt);
    (*env)->SetIntField(env, index, global_field_cache.UInt_data, (jint)capture_index);
    return NEW_OBJECT(Pair, index, match_obj);
}

const JNINativeMethod Query_methods[] = {
    {"init", "(JLjava/lang/String;)J", (void *)&query_init},
    {"cursor", "()J", (void *)&query_cursor},
    {"delete", "(JJ)V", (void *)&query_delete},
    {"getPatternCount", "()I", (void *)&query_get_pattern_count},
    {"getCaptureCount", "()I", (void *)&query_get_capture_count},
    {"getTimeoutMicros", "()J", (void *)&query_get_timeout_micros},
    {"setTimeoutMicros", "(J)V", (void *)&query_set_timeout_micros},
    {"getMatchLimit", "()I", (void *)&query_get_match_limit},
    {"setMatchLimit", "(I)V", (void *)&query_set_match_limit},
    {"setMaxStartDepth", "(I)V", (void *)&query_set_max_start_depth},
    {"didExceedMatchLimit", "()Z", (void *)&query_did_exceed_match_limit},
    {"disablePattern", "(I)V", (void *)&query_disable_pattern},
    {"startByteForPattern", "(I)I", (void *)&query_start_byte_for_pattern},
    {"endByteForPattern", "(I)I", (void *)&query_end_byte_for_pattern},
    {"isPatternRooted", "(I)Z", (void *)&query_is_pattern_rooted},
    {"isPatternNonLocal", "(I)Z", (void *)&query_is_pattern_non_local},
    {"stringCount", "()I", (void *)&query_string_count},
    {"exec", "(L" PACKAGE "Node;)V", (void *)&query_exec},
    {"nextMatch", "(L" PACKAGE "Tree;)L" PACKAGE "QueryMatch;", (void *)&query_next_match},
    {"nextCapture", "(L" PACKAGE "Tree;)Lkotlin/Pair;", (void *)&query_next_capture},
    {"captureNameForId", "(I)Ljava/lang/String;", (void *)&query_capture_name_for_id},
    {"stringValueForId", "(I)Ljava/lang/String;", (void *)&query_string_value_for_id},
    {"nativeSetByteRange", "(II)V", (void *)&query_native_set_byte_range},
    {"nativeSetPointRange", "(L" PACKAGE "Point;L" PACKAGE "Point;)V",
     (void *)&query_native_set_point_range},
    {"nativeDisableCapture", "(Ljava/lang/String;)V", (void *)&query_native_disable_capture},
    {"nativeIsPatternGuaranteedAtStep", "(I)Z",
     (void *)&query_native_is_pattern_guaranteed_at_step},
    {"predicatesForPattern", "(I)Ljava/util/List;", (void *)&query_predicates_for_pattern},
};

const size_t Query_methods_size = sizeof Query_methods / sizeof(JNINativeMethod);
