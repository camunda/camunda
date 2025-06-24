/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.console.ping;

import java.util.Map;

// @ConfigurationProperties(prefix = "camunda.console.ping")
public class PingConfiguration {
  private boolean enabled = false;
  private String clusterId;
  private String clusterName;
  private String endpoint;
  // number of minutes before the next ping
  private int pingPeriod = 60;
  private Map<String, String> properties;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
  }

  public int getPingPeriod() {
    return pingPeriod;
  }

  public void setPingPeriod(final int pingPeriod) {
    this.pingPeriod = pingPeriod;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public void setProperties(final Map<String, String> properties) {
    this.properties = properties;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "PingConfiguration(enabled="
        + isEnabled()
        + ", CusterId="
        + getClusterId()
        + ", ClusterName="
        + getClusterName()
        + ", consoleEndpoint="
        + getEndpoint()
        + ", pingPeriod="
        + getPingPeriod()
        + ", properties="
        + getProperties()
        + ")";
  }
}
