package com.mitocode.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//Sirve para desencadenar el error 404
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ModelNotFoundException extends RuntimeException {

    public ModelNotFoundException(String message) {
        super(message);
    }
}
