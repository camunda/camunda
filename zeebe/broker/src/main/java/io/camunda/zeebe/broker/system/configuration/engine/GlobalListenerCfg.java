/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.GlobalListenerConfiguration;
import java.util.ArrayList;
import java.util.List;

public class GlobalListenerCfg implements ConfigurationEntry {

  private static final String DEFAULT_RETRIES = "3";

  private List<String> eventTypes = new ArrayList<>();
  private String type;
  private String retries = DEFAULT_RETRIES;
  private boolean afterNonGlobal = false;

  public List<String> getEventTypes() {
    return eventTypes;
  }

  public void setEventTypes(final List<String> eventTypes) {
    this.eventTypes = eventTypes;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getRetries() {
    return retries;
  }

  public void setRetries(final String retries) {
    this.retries = retries;
  }

  public boolean isAfterNonGlobal() {
    return afterNonGlobal;
  }

  public void setAfterNonGlobal(final boolean afterNonGlobal) {
    this.afterNonGlobal = afterNonGlobal;
  }

  public GlobalListenerConfiguration createGlobalListenerConfiguration() {
    return new GlobalListenerConfiguration(eventTypes, type, retries, afterNonGlobal);
  }
}
