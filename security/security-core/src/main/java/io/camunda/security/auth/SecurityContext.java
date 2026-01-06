/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public record SecurityContext(
    @JsonProperty("authentication") CamundaAuthentication authentication,
    @JsonProperty("authorization") Authorization<?> authorization) {

  public static SecurityContext of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder {
    private CamundaAuthentication authentication;
    private Authorization<?> authorization;

    public Builder withAuthentication(final CamundaAuthentication authentication) {
      this.authentication = authentication;
      return this;
    }

    public Builder withAuthentication(
        final Function<CamundaAuthentication.Builder, CamundaAuthentication.Builder>
            builderFunction) {
      return withAuthentication(CamundaAuthentication.of(builderFunction));
    }

    public Builder withAuthorization(final Authorization authorization) {
      this.authorization = authorization;
      return this;
    }

    public <T> Builder withAuthorization(
        final Function<Authorization.Builder<T>, Authorization.Builder<T>> builderFunction) {
      return withAuthorization(Authorization.of(builderFunction));
    }

    public SecurityContext build() {
      return new SecurityContext(authentication, authorization);
    }
  }
}
