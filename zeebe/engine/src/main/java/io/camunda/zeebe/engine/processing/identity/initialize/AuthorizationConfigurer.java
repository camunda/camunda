/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static io.camunda.zeebe.engine.processing.identity.initialize.validate.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.zeebe.engine.processing.identity.initialize.validate.IdentifierValidator;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AuthorizationConfigurer
    implements EntityInitializationConfigurer<ConfiguredAuthorization, AuthorizationRecord> {

  private final Pattern idPattern;

  public AuthorizationConfigurer(final Pattern idPattern) {
    this.idPattern = idPattern;
  }

  @Override
  public Either<List<String>, AuthorizationRecord> configure(final ConfiguredAuthorization auth) {
    final List<String> violations = new ArrayList<>();

    validate(auth, violations);

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(auth));
  }

  /**
   * This method is temporary duplicated from gateway-rest and will be refactored in the follow-up
   * issue https://github.com/camunda/camunda/issues/40506
   */
  private void validate(final ConfiguredAuthorization request, final List<String> violations) {
    // owner validation
    IdentifierValidator.validateId(request.ownerId(), "ownerId", violations, idPattern);
    if (request.ownerType() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("ownerType"));
    }

    // resource validation
    IdentifierValidator.validateId(
        request.resourceId(),
        "resourceId",
        violations,
        idPattern,
        AuthorizationScope.WILDCARD_CHAR::equals);
    if (request.resourceType() == null) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("resourceType"));
    }

    // permissions validation
    if (request.permissions() == null || request.permissions().isEmpty()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("permissions"));
    }
  }

  private AuthorizationRecord mapToRecord(final ConfiguredAuthorization auth) {
    return new AuthorizationRecord()
        .setOwnerType(auth.ownerType())
        .setOwnerId(auth.ownerId())
        .setResourceType(auth.resourceType())
        .setResourceMatcher(AuthorizationScope.of(auth.resourceId()).getMatcher())
        .setResourceId(auth.resourceId())
        .setPermissionTypes(auth.permissions());
  }
}
