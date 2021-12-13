/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

public final class RaftCfg implements ConfigurationEntry {
  public static final boolean DEFAULT_ENABLE_PRIORITY_ELECTION = true;

  private boolean enablePriorityElection = DEFAULT_ENABLE_PRIORITY_ELECTION;

  public boolean isEnablePriorityElection() {
    return enablePriorityElection;
  }

  public void setEnablePriorityElection(final boolean enablePriorityElection) {
    this.enablePriorityElection = enablePriorityElection;
  }

  @Override
  public String toString() {
    return "RaftCfg{" + "enablePriorityElection=" + enablePriorityElection + '}';
  }
}
