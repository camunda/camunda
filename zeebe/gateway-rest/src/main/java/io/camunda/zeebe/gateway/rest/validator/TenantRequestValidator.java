/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

<<<<<<< HEAD:zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/validator/TenantRequestValidator.java
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
=======
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.TenantCreateRequest;
import io.camunda.gateway.protocol.model.TenantUpdateRequest;
import io.camunda.security.validation.TenantValidator;
>>>>>>> d5077f9d (feat: refactor http-mapping for tenants to use security-validation):gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/validator/TenantRequestValidator.java
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class TenantRequestValidator {

  private final TenantValidator tenantValidator;

  public TenantRequestValidator(final TenantValidator tenantValidator) {
    this.tenantValidator = tenantValidator;
  }

  public Optional<ProblemDetail> validateCreateRequest(final TenantCreateRequest request) {
    return validate(() -> tenantValidator.validateCreate(request.getTenantId(), request.getName()));
  }

  public Optional<ProblemDetail> validateUpdateRequest(final TenantUpdateRequest request) {
    return validate(() -> tenantValidator.validateUpdate(request.getName()));
  }

  public Optional<ProblemDetail> validateMemberRequest(
      final String tenantId, final String memberId, final EntityType memberType) {
    return validate(() -> tenantValidator.validateTenantMember(tenantId, memberId, memberType));
  }
}
