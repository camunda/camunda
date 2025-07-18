/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.auth.Authorization;
import java.util.Optional;
import java.util.function.Function;

public interface ResourceAccessController {

  <T> T doSearch(
      SecurityContext securityContext, Function<ResourceAccessChecks, T> resourceChecksApplier);

  boolean supports(SecurityContext securityContext);

  default boolean isAnonymousAuthentication(final CamundaAuthentication authentication) {
    final var claims =
        Optional.ofNullable(authentication).map(CamundaAuthentication::claims).orElse(null);

    if (claims != null && claims.containsKey(Authorization.AUTHORIZED_ANONYMOUS_USER)) {
      return ((boolean) claims.get(Authorization.AUTHORIZED_ANONYMOUS_USER));
    }
    return false;
  }
}
