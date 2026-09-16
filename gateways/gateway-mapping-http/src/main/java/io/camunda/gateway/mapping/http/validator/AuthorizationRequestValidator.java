/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ILLEGAL_CHARACTER;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.security.validation.AuthorizationValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

public final class AuthorizationRequestValidator {

  /**
   * Matches a SECRET resource id that can actually address a {@code camunda.secrets.<name>}
   * reference: the wildcard, or the full reference. The name charset mirrors {@code
   * SecretReference.REFERENCE_PATTERN} in {@code zeebe-engine}; duplicated here in a single
   * constant rather than depending on that module, which this gateway-mapping layer has no other
   * reason to pull in.
   */
  public static final Pattern SECRET_RESOURCE_ID_PATTERN =
      Pattern.compile("\\*|camunda\\.secrets\\.[\\p{Alnum}_-]+");

  private final AuthorizationValidator authorizationValidator;

  public AuthorizationRequestValidator(final AuthorizationValidator authorizationValidator) {
    this.authorizationValidator = authorizationValidator;
  }

  public Optional<ProblemDetail> validateIdBasedRequest(final AuthorizationIdBasedRequest request) {
    return validate(
        () -> {
          final List<String> violations =
              new ArrayList<>(
                  authorizationValidator.validate(
                      request.getOwnerId(),
                      request.getOwnerType(),
                      request.getResourceType(),
                      request.getResourceId(),
                      null,
                      request.getPermissionTypes() == null
                          ? Set.of()
                          : Set.copyOf(request.getPermissionTypes())));
          validateSecretResourceId(request.getResourceType(), request.getResourceId(), violations);
          return violations;
        });
  }

  /**
   * A SECRET resource id that isn't {@code *} or {@code camunda.secrets.<name>} can never match a
   * reference, so a grant using it would be silently inert (camunda/camunda#62736) — reject it up
   * front instead.
   */
  private static void validateSecretResourceId(
      final @Nullable ResourceTypeEnum resourceType,
      final @Nullable String resourceId,
      final List<String> violations) {
    if (resourceType == ResourceTypeEnum.SECRET
        && resourceId != null
        && !SECRET_RESOURCE_ID_PATTERN.matcher(resourceId).matches()) {
      violations.add(
          ERROR_MESSAGE_ILLEGAL_CHARACTER.formatted(
              "resourceId", SECRET_RESOURCE_ID_PATTERN.pattern()));
    }
  }

  public Optional<ProblemDetail> validatePropertyBasedRequest(
      final AuthorizationPropertyBasedRequest request) {
    return validate(
        () ->
            authorizationValidator.validate(
                request.getOwnerId(),
                request.getOwnerType(),
                request.getResourceType(),
                null,
                request.getResourcePropertyName(),
                request.getPermissionTypes() == null
                    ? Set.of()
                    : Set.copyOf(request.getPermissionTypes())));
  }
}
