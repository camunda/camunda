/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.security.AuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericExceptionMapper {

  public static final String GENERIC_ERROR_CODE = "serverError";
  public static final String NOT_FOUND_ERROR_CODE = "notFoundError";
  public static final String BAD_REQUEST_ERROR_CODE = "badRequestError";
  private static final String FORBIDDEN_ERROR_CODE = "forbiddenError";
  private static final String NOT_AUTHORIZED_ERROR_CODE = "notAuthorizedError";
  private static final Logger LOG = LoggerFactory.getLogger(GenericExceptionMapper.class);

  private static final Map<HttpStatus, String> HTTP_STATUS_TO_ERROR_CODE =
      Map.of(
          HttpStatus.NOT_FOUND, NOT_FOUND_ERROR_CODE,
          HttpStatus.BAD_REQUEST, BAD_REQUEST_ERROR_CODE,
          HttpStatus.FORBIDDEN, FORBIDDEN_ERROR_CODE,
          HttpStatus.UNAUTHORIZED, NOT_AUTHORIZED_ERROR_CODE);

  @Autowired private LocalizationService localizationService;
  @Autowired private AuthCookieService cookieService;

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<ErrorResponseDto> handleThrowable(
      final Throwable throwable, final HttpServletResponse response) {
    LOG.error("Mapping generic REST error", throwable);
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (throwable instanceof final ResponseStatusException responseStatusException) {
      if (responseStatusException.getStatusCode() instanceof final HttpStatus httpStatus) {
        status = httpStatus;
      }
    }

    if (status == HttpStatus.UNAUTHORIZED) {
      cookieService.createDeleteOptimizeAuthNewCookie(true).forEach(response::addCookie);
    }

    final String errorCode = HTTP_STATUS_TO_ERROR_CODE.getOrDefault(status, GENERIC_ERROR_CODE);

    final String localizedMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);

    final ErrorResponseDto errorResponseDto =
        new ErrorResponseDto(errorCode, localizedMessage, throwable.getMessage());

    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(errorResponseDto);
  }
}
