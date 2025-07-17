/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.security;

import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import java.util.Optional;
import java.util.function.Function;

public class RdbmsResourceAccessController implements ResourceAccessController {

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return resourceChecksApplier.apply(ResourceAccessChecks.disabled());
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return resourceChecksApplier.apply(ResourceAccessChecks.disabled());
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return Optional.of(securityContext).map(SecurityContext::authentication).isPresent()
        && !isAnonymousAuthentication(securityContext.authentication());
  }
}
