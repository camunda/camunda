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
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessElementStatisticsStrictContract(
    String elementId, Long active, Long canceled, Long incidents, Long completed) {

  public GeneratedProcessElementStatisticsStrictContract {
    Objects.requireNonNull(elementId, "elementId is required and must not be null");
    Objects.requireNonNull(active, "active is required and must not be null");
    Objects.requireNonNull(canceled, "canceled is required and must not be null");
    Objects.requireNonNull(incidents, "incidents is required and must not be null");
    Objects.requireNonNull(completed, "completed is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ElementIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ElementIdStep,
          ActiveStep,
          CanceledStep,
          IncidentsStep,
          CompletedStep,
          OptionalStep {
    private String elementId;
    private ContractPolicy.FieldPolicy<String> elementIdPolicy;
    private Long active;
    private ContractPolicy.FieldPolicy<Long> activePolicy;
    private Long canceled;
    private ContractPolicy.FieldPolicy<Long> canceledPolicy;
    private Long incidents;
    private ContractPolicy.FieldPolicy<Long> incidentsPolicy;
    private Long completed;
    private ContractPolicy.FieldPolicy<Long> completedPolicy;

    private Builder() {}

    @Override
    public ActiveStep elementId(
        final String elementId, final ContractPolicy.FieldPolicy<String> policy) {
      this.elementId = elementId;
      this.elementIdPolicy = policy;
      return this;
    }

    @Override
    public CanceledStep active(final Long active, final ContractPolicy.FieldPolicy<Long> policy) {
      this.active = active;
      this.activePolicy = policy;
      return this;
    }

    @Override
    public IncidentsStep canceled(
        final Long canceled, final ContractPolicy.FieldPolicy<Long> policy) {
      this.canceled = canceled;
      this.canceledPolicy = policy;
      return this;
    }

    @Override
    public CompletedStep incidents(
        final Long incidents, final ContractPolicy.FieldPolicy<Long> policy) {
      this.incidents = incidents;
      this.incidentsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep completed(
        final Long completed, final ContractPolicy.FieldPolicy<Long> policy) {
      this.completed = completed;
      this.completedPolicy = policy;
      return this;
    }

    @Override
    public GeneratedProcessElementStatisticsStrictContract build() {
      return new GeneratedProcessElementStatisticsStrictContract(
          applyRequiredPolicy(this.elementId, this.elementIdPolicy, Fields.ELEMENT_ID),
          applyRequiredPolicy(this.active, this.activePolicy, Fields.ACTIVE),
          applyRequiredPolicy(this.canceled, this.canceledPolicy, Fields.CANCELED),
          applyRequiredPolicy(this.incidents, this.incidentsPolicy, Fields.INCIDENTS),
          applyRequiredPolicy(this.completed, this.completedPolicy, Fields.COMPLETED));
    }
  }

  public interface ElementIdStep {
    ActiveStep elementId(final String elementId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ActiveStep {
    CanceledStep active(final Long active, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface CanceledStep {
    IncidentsStep canceled(final Long canceled, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface IncidentsStep {
    CompletedStep incidents(final Long incidents, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface CompletedStep {
    OptionalStep completed(final Long completed, final ContractPolicy.FieldPolicy<Long> policy);
  }

  public interface OptionalStep {
    GeneratedProcessElementStatisticsStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ELEMENT_ID =
        ContractPolicy.field("ProcessElementStatisticsResult", "elementId");
    public static final ContractPolicy.FieldRef ACTIVE =
        ContractPolicy.field("ProcessElementStatisticsResult", "active");
    public static final ContractPolicy.FieldRef CANCELED =
        ContractPolicy.field("ProcessElementStatisticsResult", "canceled");
    public static final ContractPolicy.FieldRef INCIDENTS =
        ContractPolicy.field("ProcessElementStatisticsResult", "incidents");
    public static final ContractPolicy.FieldRef COMPLETED =
        ContractPolicy.field("ProcessElementStatisticsResult", "completed");

    private Fields() {}
  }
}
