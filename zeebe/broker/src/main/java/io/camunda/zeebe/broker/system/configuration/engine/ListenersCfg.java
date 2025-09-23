/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.util.ArrayList;
import java.util.List;

public class ListenersCfg implements ConfigurationEntry {

  private List<ListenerCfg> execution = new ArrayList<>();
  private List<ListenerCfg> task = new ArrayList<>();

  public List<ListenerCfg> getExecution() {
    return execution;
  }

  public void setExecution(final List<ListenerCfg> execution) {
    this.execution = execution;
  }

  public List<ListenerCfg> getTask() {
    return task;
  }

  public void setTask(final List<ListenerCfg> task) {
    this.task = task;
  }
}
