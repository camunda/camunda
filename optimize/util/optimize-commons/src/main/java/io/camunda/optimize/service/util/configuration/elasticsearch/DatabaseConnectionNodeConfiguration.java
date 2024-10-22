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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
