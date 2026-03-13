/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
public record GeneratedCreateGlobalTaskListenerRequestStrictContract(String id) {

  public GeneratedCreateGlobalTaskListenerRequestStrictContract {
    Objects.requireNonNull(id, "id is required and must not be null");
  }

  public static IdStep builder() {
    return new Builder();
  }

  public static final class Builder implements IdStep, OptionalStep {
    private String id;

    private Builder() {}

    @Override
    public OptionalStep id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public GeneratedCreateGlobalTaskListenerRequestStrictContract build() {
      return new GeneratedCreateGlobalTaskListenerRequestStrictContract(this.id);
    }
  }

  public interface IdStep {
    OptionalStep id(final String id);
  }

  public interface OptionalStep {
    GeneratedCreateGlobalTaskListenerRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ID =
        ContractPolicy.field("CreateGlobalTaskListenerRequest", "id");

    private Fields() {}
  }
}
