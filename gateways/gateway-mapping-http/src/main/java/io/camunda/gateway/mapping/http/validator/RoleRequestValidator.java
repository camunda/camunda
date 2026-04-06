/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUpdateRequestStrictContract;
import io.camunda.security.validation.RoleValidator;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class RoleRequestValidator {

  private final RoleValidator roleValidator;

  public RoleRequestValidator(final RoleValidator roleValidator) {
    this.roleValidator = roleValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(
      final GeneratedRoleCreateRequestStrictContract request) {
    return validate(() -> roleValidator.validate(request.roleId(), request.name()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(
      final String roleId, final GeneratedRoleUpdateRequestStrictContract request) {
    return validate(() -> roleValidator.validate(roleId, request.name()));
  }

  public Optional<ProblemDetail> validateMemberRequest(
      final String roleId, final String memberId, final EntityType memberType) {
    return validate(() -> roleValidator.validateMember(roleId, memberId, memberType));
  }
}
