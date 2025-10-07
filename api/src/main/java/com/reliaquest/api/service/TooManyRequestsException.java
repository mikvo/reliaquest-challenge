package com.reliaquest.api.service;

import lombok.Data;
import lombok.experimental.StandardException;

@StandardException
public class TooManyRequestsException extends RuntimeException {
}
