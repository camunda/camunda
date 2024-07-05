/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public class ValidatorsCfg implements ConfigurationEntry {

  private int resultsOutputMaxSize = EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;

  public int getResultsOutputMaxSize() {
    return resultsOutputMaxSize;
  }

  public void setResultsOutputMaxSize(final int resultsOutputMaxSize) {
    this.resultsOutputMaxSize = resultsOutputMaxSize;
  }

  @Override
  public String toString() {
    return "BpmnValidatorsCfg{" + "resultsOutputMaxSize=" + resultsOutputMaxSize + '}';
  }
}
