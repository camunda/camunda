/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.engine.EngineConfiguration;

public record BpmnValidatorConfig(
    int maxIdFieldLength,
    int maxNameFieldLength,
    int maxWorkerTypeLength,
    int validatorResultsOutputMaxSize) {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements ObjectBuilder<BpmnValidatorConfig> {
    private int maxIdFieldLength = EngineConfiguration.DEFAULT_MAX_ID_FIELD_LENGTH;
    private int maxNameFieldLength = EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH;
    private int maxWorkerTypeLength = EngineConfiguration.DEFAULT_MAX_WORKER_TYPE_LENGTH;
    private int validatorResultsOutputMaxSize =
        EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;

    public Builder withMaxIdFieldLength(final int maxIdFieldLength) {
      this.maxIdFieldLength = maxIdFieldLength;
      return this;
    }

    public Builder withMaxNameFieldLength(final int maxNameFieldLength) {
      this.maxNameFieldLength = maxNameFieldLength;
      return this;
    }

    public Builder withMaxWorkerTypeLength(final int maxWorkerTypeLength) {
      this.maxWorkerTypeLength = maxWorkerTypeLength;
      return this;
    }

    public Builder withValidatorResultsOutputMaxSize(final int validatorResultsOutputMaxSize) {
      this.validatorResultsOutputMaxSize = validatorResultsOutputMaxSize;
      return this;
    }

    @Override
    public BpmnValidatorConfig build() {
      return new BpmnValidatorConfig(
          maxIdFieldLength, maxNameFieldLength, maxWorkerTypeLength, validatorResultsOutputMaxSize);
    }
  }
}
