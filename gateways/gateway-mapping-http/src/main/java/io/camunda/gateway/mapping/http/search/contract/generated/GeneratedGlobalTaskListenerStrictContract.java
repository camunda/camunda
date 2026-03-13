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
public record GeneratedGlobalTaskListenerStrictContract(
    String id, io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source) {

  public GeneratedGlobalTaskListenerStrictContract {
    Objects.requireNonNull(id, "id is required and must not be null");
    Objects.requireNonNull(source, "source is required and must not be null");
  }

  public static IdStep builder() {
    return new Builder();
  }

  public static final class Builder implements IdStep, SourceStep, OptionalStep {
    private String id;
    private io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source;

    private Builder() {}

    @Override
    public SourceStep id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public OptionalStep source(
        final io.camunda.gateway.protocol.model.GlobalListenerSourceEnum source) {
      this.source = source;
      return this;
    }

    @Override
    public GeneratedGlobalTaskListenerStrictContract build() {
      return new GeneratedGlobalTaskListenerStrictContract(this.id, this.source);
    }
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
    public static final ContractPolicy.FieldRef ID =
        ContractPolicy.field("GlobalTaskListenerResult", "id");
    public static final ContractPolicy.FieldRef SOURCE =
        ContractPolicy.field("GlobalTaskListenerResult", "source");

    private Fields() {}
  }
}
