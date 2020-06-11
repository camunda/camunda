/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.InternalApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.MonitoringApiCfg;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_INTERNAL_API_PORT = 26502;
  public static final int DEFAULT_MONITORING_API_PORT = 9600;
  public static final DataSize DEFAULT_MAX_MESSAGE_SIZE = DataSize.ofMegabytes(4);

  private String host = DEFAULT_HOST;
  private int portOffset = 0;
  private DataSize maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private String advertisedHost;

  private final CommandApiCfg commandApi = new CommandApiCfg();
  private InternalApiCfg internalApi = new InternalApiCfg();
  private MonitoringApiCfg monitoringApi = new MonitoringApiCfg();

  @Override
  public void init(final BrokerCfg brokerCfg, final String brokerBase) {
    applyDefaults();
  }

  public void applyDefaults() {
    commandApi.applyDefaults(this);
    internalApi.applyDefaults(this);
    monitoringApi.applyDefaults(this);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public String getAdvertisedHost() {
    return Optional.ofNullable(advertisedHost).orElse(getHost());
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public long getMaxMessageSizeInBytes() {
    return maxMessageSize.toBytes();
  }

  public DataSize getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(final DataSize maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
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
        + ", commandApi="
        + commandApi
        + ", internalApi="
        + internalApi
        + ", monitoringApi="
        + monitoringApi
        + '}';
  }
}
