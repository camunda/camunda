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
import org.jspecify.annotations.NullMarked;

@NullMarked
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
    private Long active;
    private Long canceled;
    private Long incidents;
    private Long completed;

    private Builder() {}

    @Override
    public ActiveStep elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    @Override
    public CanceledStep active(final Long active) {
      this.active = active;
      return this;
    }

    @Override
    public IncidentsStep canceled(final Long canceled) {
      this.canceled = canceled;
      return this;
    }

    @Override
    public CompletedStep incidents(final Long incidents) {
      this.incidents = incidents;
      return this;
    }

    @Override
    public OptionalStep completed(final Long completed) {
      this.completed = completed;
      return this;
    }

    @Override
    public GeneratedProcessElementStatisticsStrictContract build() {
      return new GeneratedProcessElementStatisticsStrictContract(
          this.elementId, this.active, this.canceled, this.incidents, this.completed);
    }
  }

  public interface ElementIdStep {
    ActiveStep elementId(final String elementId);
  }

  public interface ActiveStep {
    CanceledStep active(final Long active);
  }

  public interface CanceledStep {
    IncidentsStep canceled(final Long canceled);
  }

  public interface IncidentsStep {
    CompletedStep incidents(final Long incidents);
  }

  public interface CompletedStep {
    OptionalStep completed(final Long completed);
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
