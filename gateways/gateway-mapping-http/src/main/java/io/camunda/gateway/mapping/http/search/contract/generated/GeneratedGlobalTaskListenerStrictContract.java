/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/global-listeners.yaml#/components/schemas/GlobalTaskListenerResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGlobalTaskListenerStrictContract(
    String type,
    Integer retries,
    Boolean afterNonGlobal,
    Integer priority,
    java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes,
    String id,
    io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source
) {

  public GeneratedGlobalTaskListenerStrictContract {
    Objects.requireNonNull(type, "type is required and must not be null");
    Objects.requireNonNull(retries, "retries is required and must not be null");
    Objects.requireNonNull(afterNonGlobal, "afterNonGlobal is required and must not be null");
    Objects.requireNonNull(priority, "priority is required and must not be null");
    Objects.requireNonNull(eventTypes, "eventTypes is required and must not be null");
    Objects.requireNonNull(id, "id is required and must not be null");
    Objects.requireNonNull(source, "source is required and must not be null");
  }


  public static TypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements TypeStep, RetriesStep, AfterNonGlobalStep, PriorityStep, EventTypesStep, IdStep, SourceStep, OptionalStep {
    private String type;
    private Integer retries;
    private Boolean afterNonGlobal;
    private Integer priority;
    private java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes;
    private String id;
    private io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source;

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
    public IdStep eventTypes(final java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes) {
      this.eventTypes = eventTypes;
      return this;
    }

    @Override
    public SourceStep id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep source(final io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source) {
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
    IdStep eventTypes(final java.util.List<io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum> eventTypes);
  }

  public interface IdStep {
    SourceStep id(final String id);
  }

  public interface SourceStep {
    OptionalStep source(final io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source);
  }

  public interface OptionalStep {
    GeneratedGlobalTaskListenerStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE = ContractPolicy.field("GlobalTaskListenerResult", "type");
    public static final ContractPolicy.FieldRef RETRIES = ContractPolicy.field("GlobalTaskListenerResult", "retries");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL = ContractPolicy.field("GlobalTaskListenerResult", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY = ContractPolicy.field("GlobalTaskListenerResult", "priority");
    public static final ContractPolicy.FieldRef EVENT_TYPES = ContractPolicy.field("GlobalTaskListenerResult", "eventTypes");
    public static final ContractPolicy.FieldRef ID = ContractPolicy.field("GlobalTaskListenerResult", "id");
    public static final ContractPolicy.FieldRef SOURCE = ContractPolicy.field("GlobalTaskListenerResult", "source");

    private Fields() {}
  }


}
