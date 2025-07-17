/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import java.util.function.Function;

public class AnonymousResourceAccessController implements ResourceAccessController {

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doReadAnonymously(resourceChecksApplier);
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return doReadAnonymously(resourceChecksApplier);
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return isAnonymousAuthentication(securityContext.authentication());
  }

  protected <T> T doReadAnonymously(final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return resourceChecksApplier.apply(ResourceAccessChecks.disabled());
  }
}
