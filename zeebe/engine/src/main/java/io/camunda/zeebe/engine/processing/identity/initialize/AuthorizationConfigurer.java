/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;

public class AuthorizationConfigurer
    implements EntityInitializationConfigurer<ConfiguredAuthorization, AuthorizationRecord> {

  @Override
  public Either<List<String>, AuthorizationRecord> configure(final ConfiguredAuthorization auth) {
    final List<String> violations = new ArrayList<>();

    if (auth.ownerType().equals(AuthorizationOwnerType.UNSPECIFIED)) {
      // TODO: Just an example validation for now. Should use the validations currently in REST
      //   package later on.
      violations.add("Authorization owner must not be UNSPECIFIED");
    }

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
        .setResourceMatcher(getResourceMatcher(auth.resourceId()))
        .setResourceId(auth.resourceId())
        .setPermissionTypes(auth.permissions());
  }

  private AuthorizationResourceMatcher getResourceMatcher(final String resourceId) {
    if (resourceId.equals(AuthorizationScope.WILDCARD.getResourceId())) {
      return AuthorizationScope.WILDCARD.getMatcher();
    } else {
      return AuthorizationResourceMatcher.ID;
    }
  }
}
