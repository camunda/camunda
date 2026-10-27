/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.MappingRuleCreateRequest;
import io.camunda.gateway.protocol.model.MappingRuleUpdateRequest;
import io.camunda.security.validation.MappingRuleValidator;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class MappingRuleRequestValidator {

  private final MappingRuleValidator mappingRuleValidator;

  public MappingRuleRequestValidator(final MappingRuleValidator mappingRuleValidator) {
    this.mappingRuleValidator = mappingRuleValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final MappingRuleCreateRequest request) {
    return validate(
        () ->
            mappingRuleValidator.validateCreateRequest(
                request.getMappingRuleId(),
                request.getClaimName(),
                request.getClaimValue(),
                request.getName()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(final MappingRuleUpdateRequest request) {
    return validate(
        () ->
            mappingRuleValidator.validateUpdateRequest(
                request.getClaimName(), request.getClaimValue(), request.getName()));
  }
}
