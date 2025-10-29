/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.GlobalListenersConfiguration;
import java.util.ArrayList;
import java.util.List;

public class GlobalListenersCfg implements ConfigurationEntry {

  /**
   * Configures global listeners that will be applied to all user tasks within the process and will
   * be triggered during task processing.
   */
  private List<GlobalListenerCfg> userTask = new ArrayList<>();

  public List<GlobalListenerCfg> getUserTask() {
    return userTask;
  }

  public void setUserTask(final List<GlobalListenerCfg> userTask) {
    this.userTask = userTask;
  }

  public GlobalListenersConfiguration createGlobalListenersConfiguration() {
    return new GlobalListenersConfiguration(
        userTask.stream().map(GlobalListenerCfg::createGlobalListenerConfiguration).toList());
  }
}
