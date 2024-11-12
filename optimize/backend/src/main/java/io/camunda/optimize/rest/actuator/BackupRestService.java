/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.actuator;

import static io.camunda.optimize.rest.providers.GenericExceptionMapper.BAD_REQUEST_ERROR_CODE;
import static io.camunda.optimize.rest.providers.GenericExceptionMapper.GENERIC_ERROR_CODE;
import static io.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;

import io.camunda.optimize.dto.optimize.rest.BackupInfoDto;
import io.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.BackupService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeElasticsearchConnectionException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.xml.bind.ValidationException;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
@RestControllerEndpoint(id = "backups")
public class BackupRestService {

  private final BackupService backupService;
  private final LocalizationService localizationService;

  public BackupRestService(
      final BackupService backupService, final LocalizationService localizationService) {
    this.backupService = backupService;
    this.localizationService = localizationService;
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> takeBackup(
      final @RequestBody @Valid BackupRequestDto backupRequestDto) {
    backupService.triggerBackup(backupRequestDto.getBackupId());
    return new ResponseEntity<>(
        String.format(
            "{\"message\" : "
                + "\"Backup creation for ID %d has been scheduled. Use the GET API to monitor completion of backup process\"}",
            backupRequestDto.getBackupId()),
        HttpStatus.ACCEPTED);
  }

  @GetMapping(value = "/{backupId}")
  public BackupInfoDto info(final @PathVariable("backupId") @Nullable Long backupId) {
    return backupService.getSingleBackupInfo(backupId);
  }

  @GetMapping
  public List<BackupInfoDto> info() {
    return backupService.getAllBackupInfo();
  }

  @DeleteMapping(value = "/{backupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(final @PathVariable("backupId") Long backupId) {
    backupService.deleteBackup(backupId);
  }

  @ExceptionHandler(OptimizeRuntimeException.class)
  public ResponseEntity<ErrorResponseDto> handleServerException(
      final OptimizeRuntimeException exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeConfigurationException.class)
  public ResponseEntity<ErrorResponseDto> handleConfigurationException(
      final OptimizeConfigurationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeConflictException.class)
  public ResponseEntity<ErrorResponseDto> handleConflictException(
      final OptimizeConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleValidationException(
      final MethodArgumentNotValidException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponseDto(
                BAD_REQUEST_ERROR_CODE,
                localizationService.getDefaultLocaleMessageForApiErrorCode(BAD_REQUEST_ERROR_CODE),
                Optional.ofNullable(exception.getFieldError())
                    .map(FieldError::getDefaultMessage)
                    .orElse(exception.getMessage())));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponseDto> handleBadRequestException(
      final BadRequestException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleNotFoundException(
      final NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeElasticsearchConnectionException.class)
  public ResponseEntity<ErrorResponseDto> handleElasticsearchConnectionException(
      final OptimizeElasticsearchConnectionException exception) {
    // API to return bad gateway error in case of connection issues
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(getErrorResponseDto(exception));
  }

  private ErrorResponseDto getErrorResponseDto(final Throwable e) {
    final Class<?> errorClass = e.getClass();
    final String errorCode;

    if (NotFoundException.class.equals(errorClass)) {
      errorCode = NOT_FOUND_ERROR_CODE;
    } else if (BadRequestException.class.equals(errorClass)
        || ValidationException.class.equals(errorClass)) {
      errorCode = BAD_REQUEST_ERROR_CODE;
    } else if (OptimizeConflictException.class.equals(errorClass)) {
      errorCode = OptimizeConflictException.ERROR_CODE;
    } else if (OptimizeElasticsearchConnectionException.class.equals(errorClass)) {
      errorCode = OptimizeElasticsearchConnectionException.ERROR_CODE;
    } else {
      errorCode = GENERIC_ERROR_CODE;
    }

    final String localizedMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);

    return new ErrorResponseDto(errorCode, localizedMessage, e.getMessage());
  }
}
