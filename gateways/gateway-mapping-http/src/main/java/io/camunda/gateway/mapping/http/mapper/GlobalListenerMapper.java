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
import io.camunda.gateway.protocol.model.CreateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerBase;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalExecutionListenerResult;
import io.camunda.gateway.protocol.model.GlobalListenerBase;
import io.camunda.gateway.protocol.model.GlobalListenerSourceEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerBase;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerResult;
import io.camunda.gateway.protocol.model.UpdateGlobalExecutionListenerRequest;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import org.springframework.http.ProblemDetail;

public class GlobalListenerMapper {

  private final GlobalListenerRequestValidator requestValidator;

  public GlobalListenerMapper(final GlobalListenerRequestValidator requestValidator) {
    this.requestValidator = requestValidator;
  }

  // =====================================================================
  // Task listener request mapping
  // =====================================================================

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalTaskListenerCreateRequest(
      final CreateGlobalTaskListenerRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateRequest(request),
        () -> {
          final var record = new GlobalListenerRecord();
          fillDataFromGlobalTaskListenerRequest(record, request);
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
          fillDataFromGlobalTaskListenerRequest(record, new GlobalTaskListenerBase());
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
          fillDataFromGlobalTaskListenerRequest(record, request);
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
          fillDataFromGlobalTaskListenerRequest(record, new GlobalTaskListenerBase());
          record.setId(id);
          return record;
        });
  }

  // =====================================================================
  // Execution listener request mapping
  // =====================================================================

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalExecutionListenerCreateRequest(
      final CreateGlobalExecutionListenerRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateCreateExecutionListenerRequest(request),
        () -> {
          final var record = new GlobalListenerRecord();
          fillDataFromGlobalExecutionListenerRequest(record, request);
          record.setId(request.getId());
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalExecutionListenerGetRequest(
      final String id) {
    return RequestMapper.getResult(
        requestValidator.validateGetRequest(id),
        () -> {
          final var record = new GlobalListenerRecord();
          fillDataFromGlobalExecutionListenerRequest(record, new GlobalExecutionListenerBase());
          record.setId(id);
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalExecutionListenerUpdateRequest(
      final String id, final UpdateGlobalExecutionListenerRequest request) {
    return RequestMapper.getResult(
        requestValidator.validateUpdateExecutionListenerRequest(id, request),
        () -> {
          final var record = new GlobalListenerRecord();
          fillDataFromGlobalExecutionListenerRequest(record, request);
          record.setId(id);
          return record;
        });
  }

  public Either<ProblemDetail, GlobalListenerRecord> toGlobalExecutionListenerDeleteRequest(
      final String id) {
    return RequestMapper.getResult(
        requestValidator.validateDeleteRequest(id),
        () -> {
          final var record = new GlobalListenerRecord();
          fillDataFromGlobalExecutionListenerRequest(record, new GlobalExecutionListenerBase());
          record.setId(id);
          return record;
        });
  }

  // =====================================================================
  // Shared fill helpers
  // =====================================================================

  private void fillDataFromGlobalListenerRequest(
      final GlobalListenerRecord record, final GlobalListenerBase request) {
    if (request.getType() != null) {
      record.setType(request.getType());
    }
    if (request.getRetries() != null) {
      record.setRetries(request.getRetries());
    }
    if (request.getAfterNonGlobal() != null) {
      record.setAfterNonGlobal(request.getAfterNonGlobal());
    }
    if (request.getPriority() != null) {
      record.setPriority(request.getPriority());
    }

    // All external requests generate API-defined listeners
    record.setSource(GlobalListenerSource.API);
  }

  private void fillDataFromGlobalTaskListenerRequest(
      final GlobalListenerRecord record, final GlobalTaskListenerBase request) {
    fillDataFromGlobalListenerRequest(record, request);
    if (request.getEventTypes() != null) {
      record.setEventTypes(
          request.getEventTypes().stream().map(GlobalTaskListenerEventTypeEnum::getValue).toList());
    }

    // All requests related to task listeners must have the listener type set to USER_TASK
    record.setListenerType(GlobalListenerType.USER_TASK);
  }

  private void fillDataFromGlobalExecutionListenerRequest(
      final GlobalListenerRecord record, final GlobalExecutionListenerBase request) {
    fillDataFromGlobalListenerRequest(record, request);
    if (request.getEventTypes() != null) {
      record.setEventTypes(
          request.getEventTypes().stream()
              .map(GlobalExecutionListenerEventTypeEnum::getValue)
              .toList());
    }
    if (request.getElementTypes() != null) {
      record.setElementTypes(request.getElementTypes());
    }
    if (request.getCategories() != null) {
      record.setCategories(request.getCategories());
    }

    // All requests related to execution listeners must have the listener type set to EXECUTION
    record.setListenerType(GlobalListenerType.EXECUTION);
  }

  // =====================================================================
  // Task listener response mapping
  // =====================================================================

  public GlobalTaskListenerResult toGlobalTaskListenerResponse(final GlobalListenerRecord record) {
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

  // =====================================================================
  // Execution listener response mapping
  // =====================================================================

  public GlobalExecutionListenerResult toGlobalExecutionListenerResponse(
      final GlobalListenerRecord record) {
    return new GlobalExecutionListenerResult()
        .id(record.getId())
        .type(record.getType())
        .retries(record.getRetries())
        .eventTypes(
            record.getEventTypes().stream()
                .map(GlobalExecutionListenerEventTypeEnum::fromValue)
                .toList())
        .elementTypes(
            record.getElementTypes() != null ? record.getElementTypes() : Collections.emptyList())
        .categories(
            record.getCategories() != null ? record.getCategories() : Collections.emptyList())
        .afterNonGlobal(record.isAfterNonGlobal())
        .priority(record.getPriority())
        .source(GlobalListenerSourceEnum.valueOf(record.getSource().name()));
  }
}
