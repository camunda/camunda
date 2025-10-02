/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_HOST;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Grpc {

  /** Sets the address the gateway binds to */
  private String address = DEFAULT_HOST;

  /** Sets the port the gateway binds to */
  private int port = DEFAULT_PORT;

  /**
   * Sets the minimum keep alive interval. This setting specifies the minimum accepted interval
   * between keep alive pings. This value must be specified as a positive integer followed by 's'
   * for seconds, 'm' for minutes or 'h' for hours.
   */
  private Duration minKeepAliveInterval = Duration.ofSeconds(30);

  /** Sets the ssl configuration for the gateway */
  private Ssl ssl = new Ssl();

  /** Sets the interceptors */
  private List<Interceptor> interceptors = new ArrayList<>();

  /** Sets the number of threads the gateway will use to communicate with the broker cluster */
  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

  public String getAddress() {
    return address;
  }

  public void setAddress(final String address) {
    this.address = address;
  }

  public int getPort() {
    return port;
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public Duration getMinKeepAliveInterval() {
    return minKeepAliveInterval;
  }

  public void setMinKeepAliveInterval(final Duration minKeepAliveInterval) {
    this.minKeepAliveInterval = minKeepAliveInterval;
  }

  public Ssl getSsl() {
    return ssl;
  }

  public void setSsl(final Ssl ssl) {
    this.ssl = ssl;
  }

  public int getManagementThreads() {
    return managementThreads;
  }

  public void setManagementThreads(final int managementThreads) {
    this.managementThreads = managementThreads;
  }

  public List<Interceptor> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(final List<Interceptor> interceptors) {
    this.interceptors = interceptors;
  }

  @Override
  public Grpc clone() {
    final Grpc copy = new Grpc();
    copy.address = address;
    copy.port = port;
    copy.minKeepAliveInterval = minKeepAliveInterval;
    copy.ssl = ssl;
    copy.interceptors = interceptors;
    copy.managementThreads = managementThreads;

    return copy;
  }
}
