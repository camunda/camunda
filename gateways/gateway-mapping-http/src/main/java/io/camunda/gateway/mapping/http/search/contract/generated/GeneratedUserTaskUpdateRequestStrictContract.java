/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/UserTaskUpdateRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskUpdateRequestStrictContract(
    @Nullable GeneratedChangesetStrictContract changeset,
    @Nullable String action
) {

  public static GeneratedChangesetStrictContract coerceChangeset(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedChangesetStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "changeset must be a GeneratedChangesetStrictContract, but was " + value.getClass().getName());
  }



  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object changeset;
    private String action;

    private Builder() {}

    @Override
    public OptionalStep changeset(final @Nullable GeneratedChangesetStrictContract changeset) {
      this.changeset = changeset;
      return this;
    }

    @Override
    public OptionalStep changeset(final @Nullable Object changeset) {
      this.changeset = changeset;
      return this;
    }

    public Builder changeset(final @Nullable GeneratedChangesetStrictContract changeset, final ContractPolicy.FieldPolicy<GeneratedChangesetStrictContract> policy) {
      this.changeset = policy.apply(changeset, Fields.CHANGESET, null);
      return this;
    }

    @Override
    public OptionalStep changeset(final @Nullable Object changeset, final ContractPolicy.FieldPolicy<Object> policy) {
      this.changeset = policy.apply(changeset, Fields.CHANGESET, null);
      return this;
    }


    @Override
    public OptionalStep action(final @Nullable String action) {
      this.action = action;
      return this;
    }

    @Override
    public OptionalStep action(final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy) {
      this.action = policy.apply(action, Fields.ACTION, null);
      return this;
    }

    @Override
    public GeneratedUserTaskUpdateRequestStrictContract build() {
      return new GeneratedUserTaskUpdateRequestStrictContract(
          coerceChangeset(this.changeset),
          this.action);
    }
  }

  public interface OptionalStep {
  OptionalStep changeset(final @Nullable GeneratedChangesetStrictContract changeset);

  OptionalStep changeset(final @Nullable Object changeset);

  OptionalStep changeset(final @Nullable GeneratedChangesetStrictContract changeset, final ContractPolicy.FieldPolicy<GeneratedChangesetStrictContract> policy);

  OptionalStep changeset(final @Nullable Object changeset, final ContractPolicy.FieldPolicy<Object> policy);


  OptionalStep action(final @Nullable String action);

  OptionalStep action(final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedUserTaskUpdateRequestStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef CHANGESET = ContractPolicy.field("UserTaskUpdateRequest", "changeset");
    public static final ContractPolicy.FieldRef ACTION = ContractPolicy.field("UserTaskUpdateRequest", "action");

    private Fields() {}
  }


}
