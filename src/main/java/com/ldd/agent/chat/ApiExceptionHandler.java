package com.ldd.agent.chat;

import com.ldd.agent.model.ModelCallException;
import com.ldd.agent.cursor.CursorAgentException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(AgentNotAvailableException.class)
    public ResponseEntity<ApiError> agentUnavailable(AgentNotAvailableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiError.of("AGENT_NOT_AVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(ModelCallException.class)
    public ResponseEntity<ApiError> modelFailure(ModelCallException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiError.of("MODEL_CALL_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> databaseFailure(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("DATABASE_UNAVAILABLE", "数据库暂时不可用，请检查 MySQL 连接和表结构。"));
    }

    @ExceptionHandler(CursorAgentException.class)
    public ResponseEntity<ApiError> cursorFailure(CursorAgentException exception) {
        HttpStatus status = HttpStatus.resolve(exception.status());
        return ResponseEntity.status(status == null ? HttpStatus.BAD_GATEWAY : status)
                .body(ApiError.of("CURSOR_AGENT_FAILED", exception.getMessage()));
    }
}
