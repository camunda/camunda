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
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.ArrayList;
import java.util.List;

public class GlobalListenerCfg implements ConfigurationEntry {

  private String id = "";
  private List<String> eventTypes = new ArrayList<>();
  private String type = "";
  private String retries = String.valueOf(GlobalListenerRecordValue.DEFAULT_RETRIES);
  private boolean afterNonGlobal = false;
  private int priority = GlobalListenerRecordValue.DEFAULT_PRIORITY;
  private GlobalListenerType listenerType = GlobalListenerType.USER_TASK;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

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

  public int getPriority() {
    return priority;
  }

  public void setPriority(final int priority) {
    this.priority = priority;
  }

  public GlobalListenerType getListenerType() {
    return listenerType;
  }

  public void setListenerType(final GlobalListenerType listenerType) {
    this.listenerType = listenerType;
  }

  public GlobalListenerConfiguration createGlobalListenerConfiguration() {
    return new GlobalListenerConfiguration(
        id, eventTypes, type, retries, afterNonGlobal, priority, listenerType);
  }
}
