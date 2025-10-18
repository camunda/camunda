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

public class ListenersOrderCfg implements ConfigurationEntry {

  private List<ListenerCfg> beforeLocal = new ArrayList<>();
  private List<ListenerCfg> afterLocal = new ArrayList<>();

  public List<ListenerCfg> getBeforeLocal() {
    return beforeLocal;
  }

  public void setBeforeLocal(final List<ListenerCfg> beforeLocal) {
    this.beforeLocal = beforeLocal;
  }

  public List<ListenerCfg> getAfterLocal() {

    return afterLocal;
  }

  public void setAfterLocal(final List<ListenerCfg> afterLocal) {
    this.afterLocal = afterLocal;
  }
}
