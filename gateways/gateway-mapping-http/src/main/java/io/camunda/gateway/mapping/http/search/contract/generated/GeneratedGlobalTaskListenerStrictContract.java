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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalTaskListenerStrictContract(
    @JsonProperty("type") String type,
    @JsonProperty("retries") Integer retries,
    @JsonProperty("afterNonGlobal") Boolean afterNonGlobal,
    @JsonProperty("priority") Integer priority,
    @JsonProperty("eventTypes")
        java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes,
    @JsonProperty("id") String id,
    @JsonProperty("source")
        io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalListenerSourceEnum
            source) {

  public GeneratedGlobalTaskListenerStrictContract {
    Objects.requireNonNull(type, "No type provided.");
    Objects.requireNonNull(retries, "No retries provided.");
    Objects.requireNonNull(afterNonGlobal, "No afterNonGlobal provided.");
    Objects.requireNonNull(priority, "No priority provided.");
    Objects.requireNonNull(eventTypes, "No eventTypes provided.");
    Objects.requireNonNull(id, "No id provided.");
    Objects.requireNonNull(source, "No source provided.");
  }

  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements TypeStep,
          RetriesStep,
          AfterNonGlobalStep,
          PriorityStep,
          EventTypesStep,
          IdStep,
          SourceStep,
          OptionalStep {
    private String type;
    private Integer retries;
    private Boolean afterNonGlobal;
    private Integer priority;
    private java.util.List<
            io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedGlobalTaskListenerEventTypeEnum>
        eventTypes;
    private String id;
    private io.camunda.gateway.mapping.http.search.contract.generated
            .GeneratedGlobalListenerSourceEnum
        source;

    private Builder() {}

    @Override
    public RetriesStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public AfterNonGlobalStep retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public PriorityStep afterNonGlobal(final Boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    @Override
    public EventTypesStep priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public IdStep eventTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public SourceStep id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep source(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedGlobalListenerSourceEnum
            source) {
      this.source = source;
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerStrictContract build() {
      return new GeneratedGlobalTaskListenerStrictContract(
          this.type,
          this.retries,
          this.afterNonGlobal,
          this.priority,
          this.eventTypes,
          this.id,
          this.source);
    }
  }

  public interface TypeStep {
    RetriesStep type(final String type);
  }

  public interface RetriesStep {
    AfterNonGlobalStep retries(final Integer retries);
  }

  public interface AfterNonGlobalStep {
    PriorityStep afterNonGlobal(final Boolean afterNonGlobal);
  }

  public interface PriorityStep {
    EventTypesStep priority(final Integer priority);
  }

  public interface EventTypesStep {
    IdStep eventTypes(
        final java.util.List<
                io.camunda.gateway.mapping.http.search.contract.generated
                    .GeneratedGlobalTaskListenerEventTypeEnum>
            eventTypes);
  }

  public interface IdStep {
    SourceStep id(final String id);
  }

  public interface SourceStep {
    OptionalStep source(
        final io.camunda.gateway.mapping.http.search.contract.generated
                .GeneratedGlobalListenerSourceEnum
            source);
  }

  public interface OptionalStep {
    GeneratedGlobalTaskListenerStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("GlobalTaskListenerResult", "type");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("GlobalTaskListenerResult", "retries");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL =
        ContractPolicy.field("GlobalTaskListenerResult", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("GlobalTaskListenerResult", "priority");
    public static final ContractPolicy.FieldRef EVENT_TYPES =
        ContractPolicy.field("GlobalTaskListenerResult", "eventTypes");
    public static final ContractPolicy.FieldRef ID =
        ContractPolicy.field("GlobalTaskListenerResult", "id");
    public static final ContractPolicy.FieldRef SOURCE =
        ContractPolicy.field("GlobalTaskListenerResult", "source");

    private Fields() {}
  }
}
