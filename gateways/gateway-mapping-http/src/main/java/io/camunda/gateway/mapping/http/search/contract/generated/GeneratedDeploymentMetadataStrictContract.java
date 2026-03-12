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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDeploymentMetadataStrictContract(
    @Nullable GeneratedDeploymentProcessStrictContract processDefinition,
    @Nullable GeneratedDeploymentDecisionStrictContract decisionDefinition,
    @Nullable GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
    @Nullable GeneratedDeploymentFormStrictContract form,
    @Nullable GeneratedDeploymentResourceStrictContract resource) {

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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
        final GeneratedDeploymentProcessStrictContract processDefinition) {
      this.processDefinition = processDefinition;
      return this;
    }

    @Override
    public OptionalStep processDefinition(final Object processDefinition) {
      this.processDefinition = processDefinition;
      return this;
    }

    public Builder processDefinition(
        final GeneratedDeploymentProcessStrictContract processDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentProcessStrictContract> policy) {
      this.processDefinition = policy.apply(processDefinition, Fields.PROCESS_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep processDefinition(
        final Object processDefinition, final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinition = policy.apply(processDefinition, Fields.PROCESS_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(
        final GeneratedDeploymentDecisionStrictContract decisionDefinition) {
      this.decisionDefinition = decisionDefinition;
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(final Object decisionDefinition) {
      this.decisionDefinition = decisionDefinition;
      return this;
    }

    public Builder decisionDefinition(
        final GeneratedDeploymentDecisionStrictContract decisionDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionStrictContract> policy) {
      this.decisionDefinition = policy.apply(decisionDefinition, Fields.DECISION_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionDefinition(
        final Object decisionDefinition, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionDefinition = policy.apply(decisionDefinition, Fields.DECISION_DEFINITION, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(
        final GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements) {
      this.decisionRequirements = decisionRequirements;
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(final Object decisionRequirements) {
      this.decisionRequirements = decisionRequirements;
      return this;
    }

    public Builder decisionRequirements(
        final GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionRequirementsStrictContract>
            policy) {
      this.decisionRequirements =
          policy.apply(decisionRequirements, Fields.DECISION_REQUIREMENTS, null);
      return this;
    }

    @Override
    public OptionalStep decisionRequirements(
        final Object decisionRequirements, final ContractPolicy.FieldPolicy<Object> policy) {
      this.decisionRequirements =
          policy.apply(decisionRequirements, Fields.DECISION_REQUIREMENTS, null);
      return this;
    }

    @Override
    public OptionalStep form(final GeneratedDeploymentFormStrictContract form) {
      this.form = form;
      return this;
    }

    @Override
    public OptionalStep form(final Object form) {
      this.form = form;
      return this;
    }

    public Builder form(
        final GeneratedDeploymentFormStrictContract form,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentFormStrictContract> policy) {
      this.form = policy.apply(form, Fields.FORM, null);
      return this;
    }

    @Override
    public OptionalStep form(final Object form, final ContractPolicy.FieldPolicy<Object> policy) {
      this.form = policy.apply(form, Fields.FORM, null);
      return this;
    }

    @Override
    public OptionalStep resource(final GeneratedDeploymentResourceStrictContract resource) {
      this.resource = resource;
      return this;
    }

    @Override
    public OptionalStep resource(final Object resource) {
      this.resource = resource;
      return this;
    }

    public Builder resource(
        final GeneratedDeploymentResourceStrictContract resource,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentResourceStrictContract> policy) {
      this.resource = policy.apply(resource, Fields.RESOURCE, null);
      return this;
    }

    @Override
    public OptionalStep resource(
        final Object resource, final ContractPolicy.FieldPolicy<Object> policy) {
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
        final GeneratedDeploymentProcessStrictContract processDefinition);

    OptionalStep processDefinition(final Object processDefinition);

    OptionalStep processDefinition(
        final GeneratedDeploymentProcessStrictContract processDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentProcessStrictContract> policy);

    OptionalStep processDefinition(
        final Object processDefinition, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionDefinition(
        final GeneratedDeploymentDecisionStrictContract decisionDefinition);

    OptionalStep decisionDefinition(final Object decisionDefinition);

    OptionalStep decisionDefinition(
        final GeneratedDeploymentDecisionStrictContract decisionDefinition,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionStrictContract> policy);

    OptionalStep decisionDefinition(
        final Object decisionDefinition, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep decisionRequirements(
        final GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements);

    OptionalStep decisionRequirements(final Object decisionRequirements);

    OptionalStep decisionRequirements(
        final GeneratedDeploymentDecisionRequirementsStrictContract decisionRequirements,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentDecisionRequirementsStrictContract>
            policy);

    OptionalStep decisionRequirements(
        final Object decisionRequirements, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep form(final GeneratedDeploymentFormStrictContract form);

    OptionalStep form(final Object form);

    OptionalStep form(
        final GeneratedDeploymentFormStrictContract form,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentFormStrictContract> policy);

    OptionalStep form(final Object form, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep resource(final GeneratedDeploymentResourceStrictContract resource);

    OptionalStep resource(final Object resource);

    OptionalStep resource(
        final GeneratedDeploymentResourceStrictContract resource,
        final ContractPolicy.FieldPolicy<GeneratedDeploymentResourceStrictContract> policy);

    OptionalStep resource(final Object resource, final ContractPolicy.FieldPolicy<Object> policy);

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
