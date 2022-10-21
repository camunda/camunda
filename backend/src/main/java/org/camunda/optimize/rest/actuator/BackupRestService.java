/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.actuator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.camunda.optimize.dto.optimize.rest.BackupResponseDto;
import org.camunda.optimize.dto.optimize.rest.BackupStateResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.BackupService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.xml.bind.ValidationException;

import static org.camunda.optimize.rest.providers.GenericExceptionMapper.BAD_REQUEST_ERROR_CODE;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.GENERIC_ERROR_CODE;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;

@RequiredArgsConstructor
@Component
@RestControllerEndpoint(id = "backup")
@Conditional(CCSMCondition.class)
public class BackupRestService {
  private final BackupService backupService;
  private final LocalizationService localizationService;

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public BackupResponseDto takeBackup(final @RequestBody @Valid BackupRequestDto backupRequestDto) {
    return backupService.triggerBackup(backupRequestDto.getBackupId());
  }

  @GetMapping(value = "/{backupId}")
  public BackupStateResponseDto state(final @PathVariable String backupId) {
    return backupService.getBackupState(backupId);
  }

  @ExceptionHandler(OptimizeRuntimeException.class)
  public ResponseEntity<ErrorResponseDto> handleServerException(final OptimizeRuntimeException exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeConfigurationException.class)
  public ResponseEntity<ErrorResponseDto> handleConfigurationException(final OptimizeConfigurationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeConflictException.class)
  public ResponseEntity<ErrorResponseDto> handleConflictException(final OptimizeConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponseDto> handleValidationException(final ValidationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponseDto> handleBadRequestException(final BadRequestException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleNotFoundException(final NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  private ErrorResponseDto getErrorResponseDto(Throwable e) {
    final Class<?> errorClass = e.getClass();
    final String errorCode;

    if (NotFoundException.class.equals(errorClass)) {
      errorCode = NOT_FOUND_ERROR_CODE;
    } else if (BadRequestException.class.equals(errorClass) || ValidationException.class.equals(errorClass)) {
      errorCode = BAD_REQUEST_ERROR_CODE;
    } else if (OptimizeConflictException.class.equals(errorClass)) {
      errorCode = OptimizeConflictException.ERROR_CODE;
    } else {
      errorCode = GENERIC_ERROR_CODE;
    }

    final String localisedMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);

    return new ErrorResponseDto(
      errorCode,
      localisedMessage,
      e.getMessage()
    );
  }

}
