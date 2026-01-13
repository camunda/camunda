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
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.util.Either;
import java.util.List;

public class AuthorizationConfigurer
    implements EntityInitializationConfigurer<ConfiguredAuthorization, AuthorizationRecord> {

  private final AuthorizationValidator validator;

  public AuthorizationConfigurer(final AuthorizationValidator validator) {
    this.validator = validator;
  }

  @Override
  public Either<List<String>, AuthorizationRecord> configure(final ConfiguredAuthorization auth) {
    final List<String> violations =
        validator.validateIdBased(
            auth.ownerId(),
            auth.ownerType(),
            auth.resourceType(),
            auth.resourceId(),
            auth.permissions());

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(auth));
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
