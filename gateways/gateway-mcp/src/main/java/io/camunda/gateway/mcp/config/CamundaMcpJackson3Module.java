/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import tools.jackson.core.Version;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 module for registering MCP-specific mixins with Spring AI's JsonParser.
 *
 * <p>This module is auto-discovered by Spring AI's {@code JsonParser} via the service loader
 * mechanism. It registers mixins that hide internal fields (e.g., {@code tenantId}) from MCP tool
 * parameter deserialization.
 *
 * <p>Spring AI 2.0.0-M3 uses Jackson 3 ({@code tools.jackson.*}) for tool parameter handling in
 * {@code AbstractMcpToolMethodCallback}, so we must register mixins via a Jackson 3 module
 * discoverable by {@code MapperBuilder.findModules()}.
 */
public class CamundaMcpJackson3Module extends SimpleModule {

  private static final String MODULE_NAME = "CamundaMcpJackson3Module";

  public CamundaMcpJackson3Module() {
    super(MODULE_NAME, moduleVersion());
    McpMixinRegistry.registerMixins(this);
  }

  private static Version moduleVersion() {
    // Use the module version from the package (if available)
    final Package pkg = CamundaMcpJackson3Module.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null) {
      try {
        final String[] parts = pkg.getImplementationVersion().split("\\.");
        if (parts.length >= 3) {
          return new Version(
              Integer.parseInt(parts[0]),
              Integer.parseInt(parts[1]),
              Integer.parseInt(parts[2]),
              null,
              null,
              null);
        }
      } catch (final NumberFormatException e) {
        // Fall through to default version
      }
    }
    return new Version(1, 0, 0, null, null, null);
  }
}
