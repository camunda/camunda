/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.AlertEmailValidationResponseDto;
import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import io.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import io.camunda.optimize.service.exceptions.OptimizeImportDescriptionNotValidException;
import io.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import io.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import io.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // This mapper takes precedence over GenericExceptionMapper
public class OptimizeExceptionMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeExceptionMapper.class);

  @Autowired private LocalizationService localizationService;

  @ExceptionHandler(OptimizeImportDescriptionNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleReportEvaluationException(
      final OptimizeImportDescriptionNotValidException exception) {
    LOG.info("Mapping OptimizeImportDescriptionNotValidException");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(getDescriptionNotValidResponseDto(exception));
  }

  private ErrorResponseDto getDescriptionNotValidResponseDto(
      final OptimizeImportDescriptionNotValidException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();
    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }

  @ExceptionHandler(OptimizeImportDefinitionDoesNotExistException.class)
  public ResponseEntity<DefinitionExceptionResponseDto> handleDefinitionDoesNotExsistException(
      final OptimizeImportDefinitionDoesNotExistException exception) {
    LOG.info("Mapping OptimizeImportDefinitionDoesNotExistException");
    final DefinitionExceptionResponseDto errorResponseDto =
        getMissingDefinitionResponseDto(exception);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(errorResponseDto);
  }

  private DefinitionExceptionResponseDto getMissingDefinitionResponseDto(
      final OptimizeImportDefinitionDoesNotExistException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new DefinitionExceptionResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getMissingDefinitions());
  }

  @ExceptionHandler(OptimizeConflictException.class)
  public ResponseEntity<ConflictResponseDto> handleOptimizeConflictException(
      final OptimizeConflictException conflictException) {
    LOG.info("Mapping OptimizeConflictException");
    final ConflictResponseDto errorResponseDto = getConflictResponseDto(conflictException);

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(errorResponseDto);
  }

  private ConflictResponseDto getConflictResponseDto(
      final OptimizeConflictException conflictException) {
    final String errorCode = conflictException.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = conflictException.getMessage();

    return new ConflictResponseDto(
        errorCode, errorMessage, detailedErrorMessage, conflictException.getConflictedItems());
  }

  @ExceptionHandler(OptimizeAlertEmailValidationException.class)
  public ResponseEntity<AlertEmailValidationResponseDto>
      handleOptimizeAlertEmailValidationException(
          final OptimizeAlertEmailValidationException optimizeAlertEmailValidationException) {
    LOG.info("Mapping OptimizeAlertEmailValidationException");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(new AlertEmailValidationResponseDto(optimizeAlertEmailValidationException));
  }

  @ExceptionHandler(OptimizeImportForbiddenException.class)
  public ResponseEntity<DefinitionExceptionResponseDto> toResponse(
      final OptimizeImportForbiddenException exception) {
    LOG.info("Mapping OptimizeImportForbiddenException");

    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(getForbiddenDefinitionResponseDto(exception));
  }

  private DefinitionExceptionResponseDto getForbiddenDefinitionResponseDto(
      final OptimizeImportForbiddenException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new DefinitionExceptionResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getForbiddenDefinitions());
  }

  @ExceptionHandler(OptimizeImportIncorrectIndexVersionException.class)
  public ResponseEntity<ImportedIndexMismatchResponseDto> toResponse(
      final OptimizeImportIncorrectIndexVersionException exception) {
    LOG.info("Mapping OptimizeImportIncorrectIndexVersionException");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(getIndexMismatchResponseDto(exception));
  }

  private ImportedIndexMismatchResponseDto getIndexMismatchResponseDto(
      final OptimizeImportIncorrectIndexVersionException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new ImportedIndexMismatchResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getMismatchingIndices());
  }

  @ExceptionHandler(OptimizeUserOrGroupIdNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> toResponse(
      final OptimizeUserOrGroupIdNotFoundException idNotFoundException) {
    LOG.info("Mapping OptimizeUserOrGroupIdNotFoundException");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(idNotFoundException));
  }

  private ErrorResponseDto getErrorResponseDto(
      final OptimizeUserOrGroupIdNotFoundException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(errorCode, errorMessage, detailedErrorMessage);
  }
}
