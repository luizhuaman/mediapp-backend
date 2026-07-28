package com.mitocode.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.LocalDateTime;

@RestControllerAdvice
public class ResponseExceptionHandler {

    /*
    //Esta clase funciona como un interceptor de excepciones y lo muestra en formato JSON
    //Para Spring boot 2.x
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<CustomErrorResponse> handleModelNotFoundException(ModelNotFoundException ex, WebRequest request) {
        CustomErrorResponse err = new CustomErrorResponse(LocalDateTime.now(), ex.getMessage(), request.getDescription(false));

        return new ResponseEntity<>(err, HttpStatus.NOT_FOUND);
    }
     */

    /*
    @ExceptionHandler(ModelNotFoundException.class)
    public ResponseEntity<CustomErrorRecord> handleModelNotFoundException(ModelNotFoundException ex, WebRequest request) {
        CustomErrorRecord err = new CustomErrorRecord(LocalDateTime.now(), ex.getMessage(), request.getDescription(false));

        return new ResponseEntity<>(err, HttpStatus.NOT_FOUND);
    }
     */

    /*
    //Desde Spring boot 3.x
    @ExceptionHandler(ModelNotFoundException.class)
    public ProblemDetail handleModelNotFoundException(ModelNotFoundException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Model Not Found Exception");
        pd.setType(URI.create(request.getDescription(false)));
        pd.setProperty("extra1", "extra value");
        pd.setProperty("extra2", 3333333);
        return pd;
    }
    */

    @ExceptionHandler(ModelNotFoundException.class)
    public ErrorResponse handleModelNotFoundException(ModelNotFoundException ex, WebRequest request) {

        return ErrorResponse.builder(ex, HttpStatus.NOT_FOUND, ex.getMessage())
                .title("Model Not Found Exception")
                .type(URI.create(request.getDescription(false)))
                .property("extra1", "extra value")
                .property("extra2", 55)
                .build();
    }

}
