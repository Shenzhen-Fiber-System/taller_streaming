package com.ourshop.streaming.streams.errors;

import com.ourshop.streaming.webrtc.errors.StreamSessionNotFoundException;
import com.ourshop.streaming.webrtc.errors.UnsupportedWebRtcRoleException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StreamNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(StreamNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("STREAM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidStreamStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidStreamStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("INVALID_STREAM_STATE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(StreamSessionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionNotFound(StreamSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("SESSION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedWebRtcRoleException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedRole(UnsupportedWebRtcRoleException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiErrorResponse.of("NOT_IMPLEMENTED", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }
}
