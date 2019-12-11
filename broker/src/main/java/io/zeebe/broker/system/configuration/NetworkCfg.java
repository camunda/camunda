/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_ADVERTISED_HOST;
import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_HOST;
import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_PORT_OFFSET;

import io.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.InternalApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.MonitoringApiCfg;
import io.zeebe.util.ByteValue;
import io.zeebe.util.Environment;
import java.util.Optional;

public class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_INTERNAL_API_PORT = 26502;
  public static final int DEFAULT_MONITORING_API_PORT = 9600;
  public static final String DEFAULT_MAX_MESSAGE_SIZE = "4M";
  public static final int DEFAULT_MAX_MESSAGE_COUNT = 16;

  private String host = DEFAULT_HOST;
  private int portOffset = 0;
  private String maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private int maxMessageCount = DEFAULT_MAX_MESSAGE_COUNT;
  private String advertisedHost;

  private final CommandApiCfg commandApi = new CommandApiCfg();
  private InternalApiCfg internalApi = new InternalApiCfg();
  private MonitoringApiCfg monitoringApi = new MonitoringApiCfg();

  @Override
  public void init(
      final BrokerCfg brokerCfg, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
    commandApi.applyDefaults(this);
    internalApi.applyDefaults(this);
    monitoringApi.applyDefaults(this);
  }

  private void applyEnvironment(final Environment environment) {
    environment.get(ENV_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_PORT_OFFSET).ifPresent(this::setPortOffset);
    environment.get(ENV_ADVERTISED_HOST).ifPresent(this::setAdvertisedHost);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public String getAdvertisedHost() {
    return Optional.ofNullable(advertisedHost).orElse(getHost());
  }

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public ByteValue getMaxMessageSize() {
    return new ByteValue(maxMessageSize);
  }

  public void setMaxMessageSize(final String maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public int getMaxMessageCount() {
    return maxMessageCount;
  }

  public NetworkCfg setMaxMessageCount(final int maxMessageCount) {
    this.maxMessageCount = maxMessageCount;
    return this;
  }

  public CommandApiCfg getCommandApi() {
    return commandApi;
  }

  public SocketBindingCfg getMonitoringApi() {
    return monitoringApi;
  }

  public void setMonitoringApi(final MonitoringApiCfg monitoringApi) {
    this.monitoringApi = monitoringApi;
  }

  public SocketBindingCfg getInternalApi() {
    return internalApi;
  }

  public void setInternalApi(final InternalApiCfg internalApi) {
    this.internalApi = internalApi;
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", portOffset="
        + portOffset
        + '\''
        + ", maxMessageSize="
        + maxMessageSize
        + ", maxMessageCount="
        + maxMessageCount
        + ", commandApi="
        + commandApi
        + ", internalApi="
        + internalApi
        + ", monitoringApi="
        + monitoringApi
        + '}';
  }
}
