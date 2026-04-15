/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.CreateGlobalTaskListenerRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalListenerSourceEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.UpdateGlobalTaskListenerRequestContract;
import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.Either;
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
      final CreateGlobalTaskListenerRequestContract request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateRequest(request),
        () -> {
          final var record = new GlobalListenerRecord();
          if (request.type() != null) {
            record.setType(request.type());
          }
          if (request.retries() != null) {
            record.setRetries(request.retries());
          }
          if (request.afterNonGlobal() != null) {
            record.setAfterNonGlobal(request.afterNonGlobal());
          }
          if (request.priority() != null) {
            record.setPriority(request.priority());
          }
          record.setSource(GlobalListenerSource.API);
          if (request.eventTypes() != null) {
            record.setEventTypes(request.eventTypes().stream().map(e -> e.getValue()).toList());
          }
          record.setListenerType(GlobalListenerType.USER_TASK);
          record.setId(request.id());
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
      final String id, final UpdateGlobalTaskListenerRequestContract request) {
    return RequestMapper.getResult(
        requestValidator.validateUpdateRequest(id, request),
        () -> {
          final var record = new GlobalListenerRecord();
          if (request.type() != null) {
            record.setType(request.type());
          }
          if (request.retries() != null) {
            record.setRetries(request.retries());
          }
          if (request.afterNonGlobal() != null) {
            record.setAfterNonGlobal(request.afterNonGlobal());
          }
          if (request.priority() != null) {
            record.setPriority(request.priority());
          }
          record.setSource(GlobalListenerSource.API);
          if (request.eventTypes() != null) {
            record.setEventTypes(request.eventTypes().stream().map(e -> e.getValue()).toList());
          }
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

  public GlobalTaskListenerContract toGlobalListenerResponse(final GlobalListenerRecord record) {
    return GlobalTaskListenerContract.builder()
        .type(record.getType())
        .retries(record.getRetries())
        .afterNonGlobal(record.isAfterNonGlobal())
        .priority(record.getPriority())
        .eventTypes(
            record.getEventTypes().stream()
                .map(GlobalTaskListenerEventTypeEnum::fromValue)
                .toList())
        .id(record.getId())
        .source(GlobalListenerSourceEnum.valueOf(record.getSource().name()))
        .build();
  }
}
