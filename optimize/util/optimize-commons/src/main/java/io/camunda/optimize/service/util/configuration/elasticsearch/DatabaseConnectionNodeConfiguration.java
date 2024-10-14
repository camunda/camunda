/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.elasticsearch;

public class DatabaseConnectionNodeConfiguration {

  private String host;
  private Integer httpPort;

  public DatabaseConnectionNodeConfiguration() {}

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getHttpPort() {
    return httpPort;
  }

  public void setHttpPort(final Integer httpPort) {
    this.httpPort = httpPort;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseConnectionNodeConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $host = getHost();
    result = result * PRIME + ($host == null ? 43 : $host.hashCode());
    final Object $httpPort = getHttpPort();
    result = result * PRIME + ($httpPort == null ? 43 : $httpPort.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseConnectionNodeConfiguration)) {
      return false;
    }
    final DatabaseConnectionNodeConfiguration other = (DatabaseConnectionNodeConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$host = getHost();
    final Object other$host = other.getHost();
    if (this$host == null ? other$host != null : !this$host.equals(other$host)) {
      return false;
    }
    final Object this$httpPort = getHttpPort();
    final Object other$httpPort = other.getHttpPort();
    if (this$httpPort == null ? other$httpPort != null : !this$httpPort.equals(other$httpPort)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "DatabaseConnectionNodeConfiguration(host="
        + getHost()
        + ", httpPort="
        + getHttpPort()
        + ")";
  }
}
