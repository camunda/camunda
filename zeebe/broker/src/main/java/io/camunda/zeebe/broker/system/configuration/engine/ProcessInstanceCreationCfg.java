/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class ProcessInstanceCreationCfg implements ConfigurationEntry {

  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

  public boolean isBusinessIdUniquenessEnabled() {
    return businessIdUniquenessEnabled;
  }

  public void setBusinessIdUniquenessEnabled(final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  @Override
  public String toString() {
    return "ProcessInstanceCreationCfg{"
        + "businessIdUniquenessEnabled="
        + businessIdUniquenessEnabled
        + '}';
  }
}
