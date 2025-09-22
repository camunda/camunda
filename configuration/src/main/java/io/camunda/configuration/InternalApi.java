/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults;

public class InternalApi {
  /** Overrides the host used for internal broker-to-broker communication */
  private String host;

  /** Sets the port used for internal broker-to-broker communication */
  private Integer port = ConfigurationDefaults.DEFAULT_CLUSTER_PORT;

  /**
   * Controls the advertised host. This is particularly useful if your broker stands behind a proxy.
   *
   * <p>If omitted, defaults to:
   *
   * <ul>
   *   <li>If zeebe.broker.network.internalApi.host was set, use this.
   *   <li>Use the resolved value of zeebe.broker.network.advertisedHost
   * </ul>
   */
  private String advertisedHost;

  /**
   * Controls the advertised port; if omitted defaults to the port. This is particularly useful if
   * your broker stands behind a proxy.
   */
  private Integer advertisedPort;

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  public String getAdvertisedHost() {
    return advertisedHost;
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public Integer getAdvertisedPort() {
    return advertisedPort;
  }

  public void setAdvertisedPort(final Integer advertisedPort) {
    this.advertisedPort = advertisedPort;
  }

  @Override
  public InternalApi clone() {
    final InternalApi copy = new InternalApi();
    copy.host = host;
    copy.port = port;
    copy.advertisedHost = advertisedHost;
    copy.advertisedPort = advertisedPort;

    return copy;
  }
}
