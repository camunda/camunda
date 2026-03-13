/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceFilterFieldsStrictContract(
    @Nullable Object processDefinitionId,
    @Nullable Object processDefinitionName,
    @Nullable Object processDefinitionVersion,
    @Nullable Object processDefinitionVersionTag,
    @Nullable Object processDefinitionKey) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object processDefinitionId;
    private Object processDefinitionName;
    private Object processDefinitionVersion;
    private Object processDefinitionVersionTag;
    private Object processDefinitionKey;

    private Builder() {}

    @Override
    public OptionalStep processDefinitionId(final @Nullable Object processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    @Override
    public OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionId =
          policy.apply(processDefinitionId, Fields.PROCESS_DEFINITION_ID, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(final @Nullable Object processDefinitionName) {
      this.processDefinitionName = processDefinitionName;
      return this;
    }

    @Override
    public OptionalStep processDefinitionName(
        final @Nullable Object processDefinitionName,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionName =
          policy.apply(processDefinitionName, Fields.PROCESS_DEFINITION_NAME, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersion(
        final @Nullable Object processDefinitionVersion,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionVersion =
          policy.apply(processDefinitionVersion, Fields.PROCESS_DEFINITION_VERSION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable Object processDefinitionVersionTag) {
      this.processDefinitionVersionTag = processDefinitionVersionTag;
      return this;
    }

    @Override
    public OptionalStep processDefinitionVersionTag(
        final @Nullable Object processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionVersionTag =
          policy.apply(processDefinitionVersionTag, Fields.PROCESS_DEFINITION_VERSION_TAG, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public GeneratedProcessInstanceFilterFieldsStrictContract build() {
      return new GeneratedProcessInstanceFilterFieldsStrictContract(
          this.processDefinitionId,
          this.processDefinitionName,
          this.processDefinitionVersion,
          this.processDefinitionVersionTag,
          this.processDefinitionKey);
    }
  }

  public interface OptionalStep {
    OptionalStep processDefinitionId(final @Nullable Object processDefinitionId);

    OptionalStep processDefinitionId(
        final @Nullable Object processDefinitionId,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionName(final @Nullable Object processDefinitionName);

    OptionalStep processDefinitionName(
        final @Nullable Object processDefinitionName,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionVersion(final @Nullable Object processDefinitionVersion);

    OptionalStep processDefinitionVersion(
        final @Nullable Object processDefinitionVersion,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionVersionTag(final @Nullable Object processDefinitionVersionTag);

    OptionalStep processDefinitionVersionTag(
        final @Nullable Object processDefinitionVersionTag,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedProcessInstanceFilterFieldsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_ID =
        ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_NAME =
        ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionName");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION =
        ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionVersion");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_VERSION_TAG =
        ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionVersionTag");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ProcessInstanceFilterFields", "processDefinitionKey");

    private Fields() {}
  }
}
