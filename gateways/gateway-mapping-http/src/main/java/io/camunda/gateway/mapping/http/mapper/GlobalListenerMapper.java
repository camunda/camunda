/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalListenerSourceEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerResult;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class GlobalListenerMapper {

  private final GlobalListenerRequestValidator requestValidator;

  public GlobalListenerMapper(final GlobalListenerRequestValidator requestValidator) {
    this.requestValidator = requestValidator;
  }

  //
  // Request mapping
  //

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerCreateRequest(
      final CreateGlobalTaskListenerRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateRequest(request),
        () -> {
          final var record = new GlobalListenerRecord();
          if (request.getType() != null) {
            record.setType(request.getType());
          }
          Optional.ofNullable(request.getRetries()).ifPresent(record::setRetries);
          Optional.ofNullable(request.getAfterNonGlobal()).ifPresent(record::setAfterNonGlobal);
          Optional.ofNullable(request.getPriority()).ifPresent(record::setPriority);
          record.setSource(GlobalListenerSource.API);
          record.setEventTypes(request.getEventTypes().stream().map(e -> e.getValue()).toList());
          record.setListenerType(GlobalListenerType.USER_TASK);
          record.setId(request.getId());
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerGetRequest(
      final String id) {
    return RequestMapper.getResult(
        requestValidator.validateGetRequest(id),
        () -> {
          final var record = new GlobalListenerRecord();
          record.setSource(GlobalListenerSource.API);
          record.setListenerType(GlobalListenerType.USER_TASK);
          record.setId(id);
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerUpdateRequest(
      final String id, final UpdateGlobalTaskListenerRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateUpdateRequest(id, request),
        () -> {
          final var record = new GlobalListenerRecord();
          record.setType(request.getType());
          Optional.ofNullable(request.getRetries()).ifPresent(record::setRetries);
          Optional.ofNullable(request.getAfterNonGlobal()).ifPresent(record::setAfterNonGlobal);
          Optional.ofNullable(request.getPriority()).ifPresent(record::setPriority);
          record.setSource(GlobalListenerSource.API);
          record.setEventTypes(request.getEventTypes().stream().map(e -> e.getValue()).toList());
          record.setListenerType(GlobalListenerType.USER_TASK);
          record.setId(id);
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerDeleteRequest(
      final String id) {
    return RequestMapper.getResult(
        requestValidator.validateDeleteRequest(id),
        () -> {
          final var record = new GlobalListenerRecord();
          record.setSource(GlobalListenerSource.API);
          record.setListenerType(GlobalListenerType.USER_TASK);
          record.setId(id);
          return record;
        });
  }

  //
  // Response mapping
  //

  public GlobalTaskListenerResult toGlobalListenerResponse(final GlobalListenerRecord record) {
    return new GlobalTaskListenerResult()
        .id(record.getId())
        .type(record.getType())
        .retries(record.getRetries())
        .eventTypes(
            record.getEventTypes().stream()
                .map(GlobalTaskListenerEventTypeEnum::fromValue)
                .toList())
        .afterNonGlobal(record.isAfterNonGlobal())
        .priority(record.getPriority())
        .source(GlobalListenerSourceEnum.valueOf(record.getSource().name()));
  }
}
