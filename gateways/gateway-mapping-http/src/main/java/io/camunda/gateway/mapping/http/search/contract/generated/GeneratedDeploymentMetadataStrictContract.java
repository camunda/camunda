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
public record GeneratedDeploymentMetadataStrictContract(
    @JsonProperty("processDefinition")
        @Nullable GeneratedDeploymentProcessStrictContract processDefinition,
    @JsonProperty("decisionDefinition")
        @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition,
    @JsonProperty("decisionRequirements")
        @Nullable GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
    @JsonProperty("form") @Nullable GeneratedDeploymentFormStrictContract form,
    @JsonProperty("resource") @Nullable GeneratedDeploymentResourceStrictContract resource) {

  public static GeneratedDeploymentProcessStrictContract coerceProcessDefinition(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDeploymentProcessStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "processDefinition must be a GeneratedDeploymentProcessStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedDeploymentDecisionStrictContract coerceDecisionDefinition(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDeploymentDecisionStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "decisionDefinition must be a GeneratedDeploymentDecisionStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedDeploymentDecisionRequirementsStrictContract coerceDecisionRequirements(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDeploymentDecisionRequirementsStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "decisionRequirements must be a GeneratedDeploymentDecisionRequirementsStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedDeploymentFormStrictContract coerceForm(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDeploymentFormStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "form must be a GeneratedDeploymentFormStrictContract, but was "
            + value.getClass().getName());
  }

  public static GeneratedDeploymentResourceStrictContract coerceResource(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedDeploymentResourceStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "resource must be a GeneratedDeploymentResourceStrictContract, but was "
            + value.getClass().getName());
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object processDefinition;
    private Object decisionDefinition;
    private Object decisionRequirements;
    private Object form;
    private Object resource;

    private Builder() {}

    @Override
    public OptionalStep processDefinition(
        final @Nullable GeneratedDeploymentProcessStrictContract processDefinition) {
      this.processDefinition = processDefinition;
      return this;
    }

    @Override
    public OptionalStep processDefinition(final @Nullable Object processDefinition) {
      this.processDefinition = processDefinition;
      return this;
    }

    public Builder processDefinition(
        final @Nullable GeneratedDeploymentProcessStrictContract processDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentProcessStrictContract> policy) {
      this.processDefinition = policy.apply(processDefinition, Fields.PROCESS_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinition(
        final @Nullable Object processDefinition, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinition = policy.apply(processDefinition, Fields.PROCESS_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(
        final @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition) {
      this.decisionDefinition = decisionDefinition;
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(final @Nullable Object decisionDefinition) {
      this.decisionDefinition = decisionDefinition;
      return this;
    }

    public Builder decisionDefinition(
        final @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionStrictContract> policy) {
      this.decisionDefinition = policy.apply(decisionDefinition, Fields.DECISION_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(
        final @Nullable Object decisionDefinition,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinition = policy.apply(decisionDefinition, Fields.DECISION_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(
        final @Nullable GeneratedDeploymentDecisionRequirementsStrictContract
            decisionRequirements) {
      this.decisionRequirements = decisionRequirements;
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(final @Nullable Object decisionRequirements) {
      this.decisionRequirements = decisionRequirements;
      return this;
    }

    public Builder decisionRequirements(
        final @Nullable GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionRequirementsStrictContract>
            policy) {
      this.decisionRequirements =
          policy.apply(decisionRequirements, Fields.DECISION_REQUIREMENTS, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(
        final @Nullable Object decisionRequirements,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirements =
          policy.apply(decisionRequirements, Fields.DECISION_REQUIREMENTS, null);
      return this;
    }

    @Override
    public OptionalStep form(final @Nullable GeneratedDeploymentFormStrictContract form) {
      this.form = form;
      return this;
    }

    @Override
    public OptionalStep form(final @Nullable Object form) {
      this.form = form;
      return this;
    }

    public Builder form(
        final @Nullable GeneratedDeploymentFormStrictContract form,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentFormStrictContract> policy) {
      this.form = policy.apply(form, Fields.FORM, null);
      return this;
    }

    @Override
    public OptionalStep form(
        final @Nullable Object form, final ContractPolicy.FieldPolicy<Object> policy) {
      this.form = policy.apply(form, Fields.FORM, null);
      return this;
    }

    @Override
    public OptionalStep resource(
        final @Nullable GeneratedDeploymentResourceStrictContract resource) {
      this.resource = resource;
      return this;
    }

    @Override
    public OptionalStep resource(final @Nullable Object resource) {
      this.resource = resource;
      return this;
    }

    public Builder resource(
        final @Nullable GeneratedDeploymentResourceStrictContract resource,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentResourceStrictContract> policy) {
      this.resource = policy.apply(resource, Fields.RESOURCE, null);
      return this;
    }

    @Override
    public OptionalStep resource(
        final @Nullable Object resource, final ContractPolicy.FieldPolicy<Object> policy) {
      this.resource = policy.apply(resource, Fields.RESOURCE, null);
      return this;
    }

    @Override
    public GeneratedDeploymentMetadataStrictContract build() {
      return new GeneratedDeploymentMetadataStrictContract(
          coerceProcessDefinition(this.processDefinition),
          coerceDecisionDefinition(this.decisionDefinition),
          coerceDecisionRequirements(this.decisionRequirements),
          coerceForm(this.form),
          coerceResource(this.resource));
    }
  }

  public interface OptionalStep {
    OptionalStep processDefinition(
        final @Nullable GeneratedDeploymentProcessStrictContract processDefinition);

    OptionalStep processDefinition(final @Nullable Object processDefinition);

    OptionalStep processDefinition(
        final @Nullable GeneratedDeploymentProcessStrictContract processDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentProcessStrictContract> policy);

    OptionalStep processDefinition(
        final @Nullable Object processDefinition, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinition(
        final @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition);

    OptionalStep decisionDefinition(final @Nullable Object decisionDefinition);

    OptionalStep decisionDefinition(
        final @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionStrictContract> policy);

    OptionalStep decisionDefinition(
        final @Nullable Object decisionDefinition, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirements(
        final @Nullable GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements);

    OptionalStep decisionRequirements(final @Nullable Object decisionRequirements);

    OptionalStep decisionRequirements(
        final @Nullable GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionRequirementsStrictContract>
            policy);

    OptionalStep decisionRequirements(
        final @Nullable Object decisionRequirements,
        final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep form(final @Nullable GeneratedDeploymentFormStrictContract form);

    OptionalStep form(final @Nullable Object form);

    OptionalStep form(
        final @Nullable GeneratedDeploymentFormStrictContract form,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentFormStrictContract> policy);

    OptionalStep form(final @Nullable Object form, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep resource(final @Nullable GeneratedDeploymentResourceStrictContract resource);

    OptionalStep resource(final @Nullable Object resource);

    OptionalStep resource(
        final @Nullable GeneratedDeploymentResourceStrictContract resource,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentResourceStrictContract> policy);

    OptionalStep resource(
        final @Nullable Object resource, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedDeploymentMetadataStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION =
        ContractPolicy.field("DeploymentMetadataResult", "processDefinition");
    public static final ContractPolicy.FieldRef DECISION_DEFINITION =
        ContractPolicy.field("DeploymentMetadataResult", "decisionDefinition");
    public static final ContractPolicy.FieldRef DECISION_REQUIREMENTS =
        ContractPolicy.field("DeploymentMetadataResult", "decisionRequirements");
    public static final ContractPolicy.FieldRef FORM =
        ContractPolicy.field("DeploymentMetadataResult", "form");
    public static final ContractPolicy.FieldRef RESOURCE =
        ContractPolicy.field("DeploymentMetadataResult", "resource");

    private Fields() {}
  }
}
