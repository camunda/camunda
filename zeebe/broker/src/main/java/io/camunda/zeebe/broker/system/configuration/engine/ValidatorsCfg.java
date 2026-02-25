/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public class ValidatorsCfg implements ConfigurationEntry {

  private int resultsOutputMaxSize = EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;
  private int maxIdFieldLength = EngineConfiguration.DEFAULT_MAX_ID_FIELD_LENGTH;
  private int maxNameFieldLength = EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH;
  private int maxWorkerTypeLength = EngineConfiguration.DEFAULT_MAX_WORKER_TYPE_LENGTH;

  public int getResultsOutputMaxSize() {
    return resultsOutputMaxSize;
  }

  public void setResultsOutputMaxSize(final int resultsOutputMaxSize) {
    this.resultsOutputMaxSize = resultsOutputMaxSize;
  }

  public int getMaxIdFieldLength() {
    return maxIdFieldLength;
  }

  public void setMaxIdFieldLength(final int maxIdFieldLength) {
    this.maxIdFieldLength = maxIdFieldLength;
  }

  public int getMaxNameFieldLength() {
    return maxNameFieldLength;
  }

  public void setMaxNameFieldLength(final int maxNameFieldLength) {
    this.maxNameFieldLength = maxNameFieldLength;
  }

  @Override
  public String toString() {
    return "BpmnValidatorsCfg{"
        + "resultsOutputMaxSize="
        + resultsOutputMaxSize
        + ", maxIdFieldLength="
        + maxIdFieldLength
        + ", maxNameFieldLength="
        + maxNameFieldLength
        + ", maxWorkerTypeLength="
        + maxWorkerTypeLength
        + '}';
  }

  public int getMaxWorkerTypeLength() {
    return maxWorkerTypeLength;
  }

  public void setMaxWorkerTypeLength(final int maxWorkerTypeLength) {
    this.maxWorkerTypeLength = maxWorkerTypeLength;
  }
}
