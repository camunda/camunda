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
import io.camunda.security.auth.condition.AuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;
import java.util.function.Function;

/**
 * Represents the security context for the current operation, containing both authentication and
 * authorization information. It encapsulates the user's identity, group and tenant affiliations,
 * along with the permissions that need to be checked for the current operation.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SecurityContext(
    @JsonProperty("authentication") CamundaAuthentication authentication,
    @JsonProperty("authorizationCondition") AuthorizationCondition authorizationCondition) {

  public static SecurityContext of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder {
    private CamundaAuthentication authentication;
    private AuthorizationCondition authorizationCondition;

    public Builder withAuthentication(final CamundaAuthentication authentication) {
      this.authentication = authentication;
      return this;
    }

    public Builder withAuthentication(
        final Function<CamundaAuthentication.Builder, CamundaAuthentication.Builder>
            builderFunction) {
      return withAuthentication(CamundaAuthentication.of(builderFunction));
    }

    public Builder withAuthorization(final Authorization<?> authorization) {
      return withAuthorizationCondition(AuthorizationConditions.single(authorization));
    }

    public <T> Builder withAuthorization(
        final Function<Authorization.Builder<T>, Authorization.Builder<T>> builderFunction) {
      return withAuthorization(Authorization.of(builderFunction));
    }

    public Builder withAuthorizationCondition(final AuthorizationCondition authorizationCondition) {
      this.authorizationCondition = authorizationCondition;
      return this;
    }

    public SecurityContext build() {
      return new SecurityContext(authentication, authorizationCondition);
    }
  }
}
