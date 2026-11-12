/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_PROBE_RESOURCE_ID;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_SECRET_RESOURCE_ID;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.AuthorizationIdBasedRequest;
import io.camunda.gateway.protocol.model.AuthorizationPropertyBasedRequest;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.security.api.model.authz.AuthorizationScope;
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
   * Charset and length for the {@code <name>} portion of a {@code camunda.secrets.<name>}
   * reference. The charset mirrors {@code SecretReference.REFERENCE_PATTERN} in {@code
   * zeebe-engine}. The length bound mirrors {@code SecretServices.MAX_REFERENCE_LENGTH} (256) minus
   * {@code SecretServices.REFERENCE_PREFIX}'s own length (16): a name any longer could never fit in
   * a resolvable reference.
   */
  public static final String SECRET_NAME_PATTERN = "[\\p{Alnum}_-]{1,240}";

  /**
   * Matches a SECRET resource id that can actually address a {@code camunda.secrets.<name>}
   * reference: the wildcard, or the full reference.
   */
  public static final Pattern SECRET_RESOURCE_ID_PATTERN =
      Pattern.compile("\\*|camunda\\.secrets\\." + SECRET_NAME_PATTERN);

  /**
   * The longest probe ID the engine accepts, counted in {@code char}s like the engine's own check.
   */
  public static final int MAX_PROBE_ID_LENGTH = 256;

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
                      resourceIdForGenericCheck(request.getResourceType(), request.getResourceId()),
                      null,
                      request.getPermissionTypes() == null
                          ? Set.of()
                          : Set.copyOf(request.getPermissionTypes())));
          validateSecretResourceId(request.getResourceType(), request.getResourceId(), violations);
          validateProbeResourceId(request.getResourceType(), request.getResourceId(), violations);
          return violations;
        });
  }

  /**
   * A probe ID is an opaque string, for example {@code
   * validate-configuration/io.camunda:aws-credential:1}, so the generic ID pattern would reject it
   * for characters such as {@code /} and {@code :}. The generic check gets the wildcard instead,
   * and {@link #validateProbeResourceId} checks the probe ID.
   */
  private static @Nullable String resourceIdForGenericCheck(
      final @Nullable ResourceTypeEnum resourceType, final @Nullable String resourceId) {
    if (resourceType == ResourceTypeEnum.PROBE && resourceId != null && !resourceId.isBlank()) {
      return AuthorizationScope.WILDCARD_CHAR;
    }
    return resourceId;
  }

  private static void validateProbeResourceId(
      final @Nullable ResourceTypeEnum resourceType,
      final @Nullable String resourceId,
      final List<String> violations) {
    if (resourceType == ResourceTypeEnum.PROBE
        && resourceId != null
        && !resourceId.isBlank()
        && !isValidProbeResourceId(resourceId)) {
      violations.add(ERROR_MESSAGE_INVALID_PROBE_RESOURCE_ID.formatted(resourceId));
    }
  }

  /**
   * The wildcard, or a probe ID the engine would accept when the probe is created: 1 to {@link
   * #MAX_PROBE_ID_LENGTH} characters, none of them whitespace by {@link Character#isWhitespace}. A
   * probe ID can never be {@code *}, so the wildcard always means every probe. The rule mirrors the
   * engine's on purpose: a grant on an ID the engine rejects could never match a probe.
   */
  private static boolean isValidProbeResourceId(final String resourceId) {
    return resourceId.length() <= MAX_PROBE_ID_LENGTH
        && resourceId.chars().noneMatch(Character::isWhitespace);
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
          ERROR_MESSAGE_INVALID_SECRET_RESOURCE_ID.formatted(resourceId, SECRET_NAME_PATTERN));
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
