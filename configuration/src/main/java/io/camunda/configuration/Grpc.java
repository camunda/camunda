/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_PORT;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Grpc {
  private static final String PREFIX = "camunda.api.grpc.";
  private static final Set<String> LEGACY_ADDRESS_PROPERTIES = Set.of("zeebe.gateway.network.host");
  private static final Set<String> LEGACY_PORT_PROPERTIES = Set.of("zeebe.gateway.network.port");
  private static final Set<String> LEGACY_MANAGEMENTTHREADS_PROPERTIES =
      Set.of("zeebe.gateway.threads.managementThreads");

  /** Sets the address the gateway binds to */
  private String address;

  /** Sets the port the gateway binds to */
  private int port = DEFAULT_PORT;

  /** Sets the ssl configuration for the gateway */
  private Ssl ssl = new Ssl();

  /** Sets the interceptors */
  private List<Interceptor> interceptors = new ArrayList<>();

  /** Sets the number of threads the gateway will use to communicate with the broker cluster */
  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

  public String getAddress() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "address",
        address,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ADDRESS_PROPERTIES);
  }

  public void setAddress(final String address) {
    this.address = address;
  }

  public int getPort() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "port",
        port,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_PORT_PROPERTIES);
  }

  public void setPort(final int port) {
    this.port = port;
  }

  public Ssl getSsl() {
    return ssl;
  }

  public void setSsl(final Ssl ssl) {
    this.ssl = ssl;
  }

  public int getManagementThreads() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "management-threads",
        managementThreads,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MANAGEMENTTHREADS_PROPERTIES);
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
}
