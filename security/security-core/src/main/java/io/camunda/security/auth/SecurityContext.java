/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the security context for the current operation, containing both authentication and
 * authorization information. It encapsulates the user's identity, group and tenant affiliations,
 * along with the permissions that need to be checked for the current operation.
 *
 * <p><strong>Note:</strong> For now, we only support a single authorization check. This will be
 * later extended to more than one authorization (for composite permissions checks).
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public final class SecurityContext {
  private final Authentication authentication;
  private final Authorization authorization;

  /** */
  @JsonCreator
  public SecurityContext(
      final @JsonProperty("authentication") Authentication authentication,
      final @JsonProperty("authorization") Authorization authorization) {
    this.authentication = authentication;
    this.authorization = authorization;
  }

  public boolean requiresAuthorizationChecks() {
    return authentication != null && authorization != null;
  }

  public static SecurityContext of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static SecurityContext withoutAuthentication() {
    return new Builder().build();
  }

  public Authentication authentication() {
    return authentication;
  }

  public Authorization authorization() {
    return authorization;
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, authorization);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final SecurityContext that = (SecurityContext) obj;
    return Objects.equals(authentication, that.authentication)
        && Objects.equals(authorization, that.authorization);
  }

  @Override
  public String toString() {
    return "SecurityContext["
        + "authentication="
        + authentication
        + ", "
        + "authorization="
        + authorization
        + ']';
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
