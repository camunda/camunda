/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedAuthorizationCreateStrictContract(
    @JsonProperty("authorizationKey") String authorizationKey) {

  public GeneratedAuthorizationCreateStrictContract {
    Objects.requireNonNull(authorizationKey, "No authorizationKey provided.");
  }

  public static String coerceAuthorizationKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "authorizationKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static AuthorizationKeyStep builder() {
    return new Builder();
  }

  public static final class Builder implements AuthorizationKeyStep, OptionalStep {
    private Object authorizationKey;

    private Builder() {}

    @Override
    public OptionalStep authorizationKey(final Object authorizationKey) {
      this.authorizationKey = authorizationKey;
      return this;
    }

    @Override
    public GeneratedAuthorizationCreateStrictContract build() {
      return new GeneratedAuthorizationCreateStrictContract(
          coerceAuthorizationKey(this.authorizationKey));
    }
  }

  public interface AuthorizationKeyStep {
    OptionalStep authorizationKey(final Object authorizationKey);
  }

  public interface OptionalStep {
    GeneratedAuthorizationCreateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef AUTHORIZATION_KEY =
        ContractPolicy.field("AuthorizationCreateResult", "authorizationKey");

    private Fields() {}
  }
}
