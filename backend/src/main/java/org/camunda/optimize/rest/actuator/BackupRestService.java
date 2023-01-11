/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.actuator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.rest.BackupInfoDto;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.BackupService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeElasticsearchConnectionException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.annotation.Conditional;
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

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.xml.bind.ValidationException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.rest.providers.GenericExceptionMapper.BAD_REQUEST_ERROR_CODE;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.GENERIC_ERROR_CODE;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;

@RequiredArgsConstructor
@Component
@RestControllerEndpoint(id = "backups")
@Conditional(CamundaCloudCondition.class)
public class BackupRestService {
  private final BackupService backupService;
  private final LocalizationService localizationService;

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<String> takeBackup(final @RequestBody @Valid BackupRequestDto backupRequestDto) {
    backupService.triggerBackup(backupRequestDto.getBackupId());
    return new ResponseEntity<>(
      String.format(
        "{\"message\" : " +
          "\"Backup creation for ID %s has been scheduled. Use the GET API to monitor completion of backup process\"}",
        backupRequestDto.getBackupId()
      ),
      HttpStatus.ACCEPTED
    );
  }

  @GetMapping(value = "/{backupId}")
  public BackupInfoDto info(final @PathVariable @Nullable String backupId) {
    return backupService.getSingleBackupInfo(backupId);
  }

  @GetMapping
  public List<BackupInfoDto> info() {
    return backupService.getAllBackupInfo();
  }

  @DeleteMapping(value = "/{backupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(final @PathVariable String backupId) {
    backupService.deleteBackup(backupId);
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

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleValidationException(final MethodArgumentNotValidException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(new ErrorResponseDto(
        BAD_REQUEST_ERROR_CODE,
        localizationService.getDefaultLocaleMessageForApiErrorCode(BAD_REQUEST_ERROR_CODE),
        Optional.ofNullable(exception.getFieldError()).map(FieldError::getDefaultMessage).orElse(exception.getMessage())
      ));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorResponseDto> handleBadRequestException(final BadRequestException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleNotFoundException(final NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .contentType(MediaType.APPLICATION_JSON)
      .body(getErrorResponseDto(exception));
  }

  @ExceptionHandler(OptimizeElasticsearchConnectionException.class)
  public ResponseEntity<ErrorResponseDto> handleElasticsearchConnectionException(final OptimizeElasticsearchConnectionException exception) {
    // API to return bad gateway error in case of connection issues
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
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
    } else if (OptimizeElasticsearchConnectionException.class.equals(errorClass)) {
      errorCode = OptimizeElasticsearchConnectionException.ERROR_CODE;
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
