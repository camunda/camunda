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
import java.util.Map;
import java.util.Set;

public class Grpc {
  private static final String PREFIX = "camunda.api.grpc.";
  private static final Map<String, String> LEGACY_GATEWAY_PROPERTIES =
      Map.of(
          "host", "zeebe.gateway.network.host",
          "port", "zeebe.gateway.network.port",
          "managementThreads", "zeebe.gateway.threads.managementThreads");
  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "host", "zeebe.broker.gateway.network.host",
          "port", "zeebe.broker.gateway.network.port",
          "managementThreads", "zeebe.broker.gateway.threads.managementThreads");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;

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
        Set.of(legacyPropertiesMap.get("host")));
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
        Set.of(legacyPropertiesMap.get("port")));
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
        Set.of(legacyPropertiesMap.get("managementThreads")));
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
    copy.ssl = ssl.clone();
    copy.interceptors = interceptors.stream().map(Interceptor::clone).toList();
    copy.managementThreads = managementThreads;

    return copy;
  }

  public Grpc withBrokerNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;
    return copy;
  }

  public Grpc withGatewayNetworkProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_PROPERTIES;
    return copy;
  }
}
