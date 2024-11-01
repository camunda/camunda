/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.util.function.Function;

/**
 * Represents the security context for the current operation, containing both authentication and
 * authorization information. It encapsulates the user's identity, group and tenant affiliations,
 * along with the permissions that need to be checked for the current operation.
 *
 * <p><strong>Note:</strong> For now, we only support a single authorization check. This will be
 * later extended to more than one authorization (for composite permissions checks).
 */
public record SecurityContext(Authentication authentication, Authorization authorization) {

  public boolean requiresAuthorizationChecks() {
    return authentication != null && authorization != null;
  }

  public static SecurityContext of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static SecurityContext withoutAuthentication() {
    return new Builder().build();
  }

  public static class Builder {
    private Authentication authentication;
    private Authorization authorization;

    public Builder withAuthentication(final Authentication authentication) {
      this.authentication = authentication;
      return this;
    }

    public Builder withAuthentication(
        final Function<Authentication.Builder, Authentication.Builder> builderFunction) {
      return withAuthentication(Authentication.of(builderFunction));
    }

    public Builder withAuthorization(final Authorization authorization) {
      this.authorization = authorization;
      return this;
    }

    public Builder withAuthorization(
        final Function<Authorization.Builder, Authorization.Builder> builderFunction) {
      return withAuthorization(Authorization.of(builderFunction));
    }

    public SecurityContext build() {
      return new SecurityContext(authentication, authorization);
    }
  }
}
