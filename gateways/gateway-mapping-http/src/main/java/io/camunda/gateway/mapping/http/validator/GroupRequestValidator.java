/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.GroupCreateRequest;
import io.camunda.gateway.protocol.model.GroupUpdateRequest;
import io.camunda.security.validation.GroupValidator;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class GroupRequestValidator {

  private final GroupValidator groupValidator;

  public GroupRequestValidator(final GroupValidator groupValidator) {
    this.groupValidator = groupValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final GroupCreateRequest request) {
    return validate(() -> groupValidator.validate(request.getGroupId(), request.getName()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(
      final String groupId, final GroupUpdateRequest request) {
    return validate(() -> groupValidator.validate(groupId, request.getName()));
  }

  public Optional<ProblemDetail> validateMemberRequest(
      final String roleId, final String memberId, final EntityType memberType) {
    return validate(() -> groupValidator.validateMember(roleId, memberId, memberType));
  }
}
