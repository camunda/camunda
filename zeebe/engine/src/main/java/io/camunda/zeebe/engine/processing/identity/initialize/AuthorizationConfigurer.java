/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class AuthorizationConfigurer
    implements EntityInitializationConfigurer<ConfiguredAuthorization, AuthorizationRecord> {

  private static final String ERROR_MUTUALLY_EXCLUSIVE_IDENTIFIERS =
      "resourceId and resourcePropertyName are mutually exclusive. Provide only one of them.";
  private static final String ERROR_MISSING_IDENTIFIER =
      "Either resourceId or resourcePropertyName must be provided.";

  private final AuthorizationValidator validator;

  public AuthorizationConfigurer(final AuthorizationValidator validator) {
    this.validator = validator;
  }

  @Override
  public Either<List<String>, AuthorizationRecord> configure(final ConfiguredAuthorization auth) {
    final boolean hasResourceId = auth.isIdBased();
    final boolean hasPropertyName = auth.isPropertyBased();

    if (hasResourceId && hasPropertyName) {
      return Either.left(List.of(ERROR_MUTUALLY_EXCLUSIVE_IDENTIFIERS));
    }

    if (!hasResourceId && !hasPropertyName) {
      return Either.left(List.of(ERROR_MISSING_IDENTIFIER));
    }

    final var violations =
        hasResourceId
            ? validator.validateIdBased(
                auth.ownerId(),
                auth.ownerType(),
                auth.resourceType(),
                auth.resourceId(),
                auth.permissions())
            : validator.validatePropertyBased(
                auth.ownerId(),
                auth.ownerType(),
                auth.resourceType(),
                auth.resourcePropertyName(),
                auth.permissions());

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(auth));
  }

  private AuthorizationRecord mapToRecord(final ConfiguredAuthorization auth) {
    final AuthorizationRecord record =
        new AuthorizationRecord()
            .setOwnerType(auth.ownerType())
            .setOwnerId(auth.ownerId())
            .setResourceType(auth.resourceType())
            .setPermissionTypes(auth.permissions());

    if (auth.isIdBased()) {
      record
          .setResourceMatcher(AuthorizationScope.of(auth.resourceId()).getMatcher())
          .setResourceId(auth.resourceId());
    } else {
      record
          .setResourceMatcher(AuthorizationResourceMatcher.PROPERTY)
          .setResourcePropertyName(auth.resourcePropertyName());
    }

    return record;
  }
}
