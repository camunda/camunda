/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Section of the new Unified Configuration system that wraps all the config keys that belong to the
 * proprietary, configurable Camunda components.
 */
@ConfigurationProperties(prefix = Camunda.PREFIX)
public class Camunda {

  public static final String PREFIX = "camunda";

  @NestedConfigurationProperty private Cluster cluster = new Cluster();
  @NestedConfigurationProperty private System system = new System();
  @NestedConfigurationProperty private Data data = new Data();
  @NestedConfigurationProperty private Api api = new Api();
  @NestedConfigurationProperty private Processing processing = new Processing();
  @NestedConfigurationProperty private Monitoring monitoring = new Monitoring();

  public Cluster getCluster() {
    return cluster;
  }

  public void setCluster(final Cluster cluster) {
    this.cluster = cluster;
  }

  public System getSystem() {
    return system;
  }

  public void setSystem(final System system) {
    this.system = system;
  }

  public Data getData() {
    return data;
  }

  public void setData(final Data data) {
    this.data = data;
  }

  public Api getApi() {
    return api;
  }

  public void setApi(final Api api) {
    this.api = api;
  }

  public Processing getProcessing() {
    return processing;
  }

  public void setProcessing(final Processing processing) {
    this.processing = processing;
  }

  public Monitoring getMonitoring() {
    return monitoring;
  }

  public void setMonitoring(final Monitoring monitoring) {
    this.monitoring = monitoring;
  }
}
