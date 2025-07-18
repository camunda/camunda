/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.util.List;

public final class RestoreCfg implements ConfigurationEntry {

  private List<String> ignoreFilesInTarget = List.of("lost+found");

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    // No special initialization required for restore configuration
  }

  public List<String> getIgnoreFilesInTarget() {
    return ignoreFilesInTarget;
  }

  public void setIgnoreFilesInTarget(final List<String> ignoreFilesInTarget) {
    this.ignoreFilesInTarget = ignoreFilesInTarget;
  }

  @Override
  public String toString() {
    return "RestoreCfg{" + "ignoreFilesInTarget=" + ignoreFilesInTarget + '}';
  }
}