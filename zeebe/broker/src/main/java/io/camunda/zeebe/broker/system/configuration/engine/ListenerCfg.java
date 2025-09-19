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

public class ListenerCfg implements ConfigurationEntry {

  private static final String DEFAULT_RETRIES = "3";

  private String eventType;
  private String jobType;
  private String retries = DEFAULT_RETRIES;
  private List<String> elementTypes = new ArrayList<>();

  public String getEventType() {
    return eventType;
  }

  public void setEventType(final String eventType) {
    this.eventType = eventType;
  }

  public String getJobType() {
    return jobType;
  }

  public void setJobType(final String jobType) {
    this.jobType = jobType;
  }

  public String getRetries() {
    return retries;
  }

  public void setRetries(final String retries) {
    this.retries = retries;
  }

  public List<String> getElementTypes() {
    return elementTypes;
  }

  public void setElementTypes(final List<String> elementTypes) {
    this.elementTypes = elementTypes;
  }
}
