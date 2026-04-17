/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.RoleRequestValidator;
import io.camunda.gateway.protocol.model.RoleCreateRequest;
import io.camunda.gateway.protocol.model.RoleUpdateRequest;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class RoleMapper {

  private final RoleRequestValidator roleRequestValidator;

  public RoleMapper(final RoleRequestValidator roleRequestValidator) {
    this.roleRequestValidator = roleRequestValidator;
  }

  public Either<ProblemDetail, CreateRoleRequest> toRoleCreateRequest(
      final RoleCreateRequest roleCreateRequest) {
    return RequestMapper.getResult(
        roleRequestValidator.validateCreateRequest(roleCreateRequest),
        () ->
            new CreateRoleRequest(
                roleCreateRequest.getRoleId(),
                roleCreateRequest.getName(),
                roleCreateRequest.getDescription()));
  }

  public Either<ProblemDetail, UpdateRoleRequest> toRoleUpdateRequest(
      final RoleUpdateRequest roleUpdateRequest, final String roleId) {
    return RequestMapper.getResult(
        roleRequestValidator.validateUpdateRequest(roleId, roleUpdateRequest),
        () ->
            new UpdateRoleRequest(
                roleId, roleUpdateRequest.getName(), roleUpdateRequest.getDescription()));
  }

  public Either<ProblemDetail, RoleMemberRequest> toRoleMemberRequest(
      final String roleId, final String memberId, final EntityType entityType) {
    return RequestMapper.getResult(
        roleRequestValidator.validateMemberRequest(roleId, memberId, entityType),
        () -> new RoleMemberRequest(roleId, memberId, entityType));
  }
}
