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
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskAssignmentRequestStrictContract(
    @JsonProperty("assignee") @Nullable String assignee,
    @JsonProperty("allowOverride") @Nullable Boolean allowOverride,
    @JsonProperty("action") @Nullable String action) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String assignee;
    private Boolean allowOverride;
    private String action;

    private Builder() {}

    @Override
    public OptionalStep assignee(final @Nullable String assignee) {
      this.assignee = assignee;
      return this;
    }

    @Override
    public OptionalStep assignee(
        final @Nullable String assignee, final ContractPolicy.FieldPolicy<String> policy) {
      this.assignee = policy.apply(assignee, Fields.ASSIGNEE, null);
      return this;
    }

    @Override
    public OptionalStep allowOverride(final @Nullable Boolean allowOverride) {
      this.allowOverride = allowOverride;
      return this;
    }

    @Override
    public OptionalStep allowOverride(
        final @Nullable Boolean allowOverride, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.allowOverride = policy.apply(allowOverride, Fields.ALLOW_OVERRIDE, null);
      return this;
    }

    @Override
    public OptionalStep action(final @Nullable String action) {
      this.action = action;
      return this;
    }

    @Override
    public OptionalStep action(
        final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy) {
      this.action = policy.apply(action, Fields.ACTION, null);
      return this;
    }

    @Override
    public GeneratedUserTaskAssignmentRequestStrictContract build() {
      return new GeneratedUserTaskAssignmentRequestStrictContract(
          this.assignee, this.allowOverride, this.action);
    }
  }

  public interface OptionalStep {
    OptionalStep assignee(final @Nullable String assignee);

    OptionalStep assignee(
        final @Nullable String assignee, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep allowOverride(final @Nullable Boolean allowOverride);

    OptionalStep allowOverride(
        final @Nullable Boolean allowOverride, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep action(final @Nullable String action);

    OptionalStep action(
        final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserTaskAssignmentRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ASSIGNEE =
        ContractPolicy.field("UserTaskAssignmentRequest", "assignee");
    public static final ContractPolicy.FieldRef ALLOW_OVERRIDE =
        ContractPolicy.field("UserTaskAssignmentRequest", "allowOverride");
    public static final ContractPolicy.FieldRef ACTION =
        ContractPolicy.field("UserTaskAssignmentRequest", "action");

    private Fields() {}
  }
}
