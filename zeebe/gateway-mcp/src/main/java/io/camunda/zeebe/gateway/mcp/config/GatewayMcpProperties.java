/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.gateway.mcp")
public class GatewayMcpProperties {

  /** Whether the MCP server is enabled. Default is false. */
  private boolean enabled = false;

  /** MCP server name exposed to MCP clients. */
  private String serverName = "Camunda Orchestration cluster MCP";

  /**
   * Camunda Orchestration cluster MCP Implementation version. TODO(mathieu) change this to be the
   * project version.
   */
  private String version = "1.0.0";

  private Duration requestTimeout = Duration.ofSeconds(20);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(final String serverName) {
    this.serverName = serverName;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }
}
