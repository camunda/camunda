/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.TenantRequestValidator;
import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.gateway.protocol.model.TenantUpdateRequest;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class TenantMapper {

  private final TenantRequestValidator tenantRequestValidator;

  public TenantMapper(final TenantRequestValidator tenantRequestValidator) {
    this.tenantRequestValidator = tenantRequestValidator;
  }

  public Either<ProblemDetail, TenantMemberRequest> toTenantMemberRequest(
      final String tenantId, final String memberId, final EntityType entityType) {
    return RequestMapper.getResult(
        tenantRequestValidator.validateMemberRequest(tenantId, memberId, entityType),
        () -> new TenantMemberRequest(tenantId, memberId, entityType));
  }

  public Either<ProblemDetail, TenantRequest> toTenantCreateDto(final TenantCreateRequest request) {
    return RequestMapper.getResult(
        tenantRequestValidator.validateCreateRequest(request),
        () ->
            new TenantRequest(
                null,
                request.getTenantId(),
                request.getName(),
                request.getDescription().orElse(null)));
  }

  public Either<ProblemDetail, TenantRequest> toTenantUpdateDto(
      final String tenantId, final TenantUpdateRequest request) {
    return RequestMapper.getResult(
        tenantRequestValidator.validateUpdateRequest(request),
        () ->
            new TenantRequest(
                null, tenantId, request.getName(), request.getDescription().orElse(null)));
  }
}
