package dev.ledgerguard.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException notFound(String kind, Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", kind + " " + id + " was not found");
    }
}
