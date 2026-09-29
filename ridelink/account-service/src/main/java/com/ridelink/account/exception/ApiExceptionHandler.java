package com.ridelink.account.exception;

import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handle(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create("https://ridelink.local/problems/" + ex.getStatus().name().toLowerCase()));
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }
}
