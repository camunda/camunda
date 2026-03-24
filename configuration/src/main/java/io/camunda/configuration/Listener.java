/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import java.util.ArrayList;
import java.util.List;

public class Listener {

  private List<GlobalListenerCfg> execution = new ArrayList<>();

  public List<GlobalListenerCfg> getExecution() {
    return execution;
  }

  public void setExecution(final List<GlobalListenerCfg> execution) {
    this.execution = execution;
  }
}
