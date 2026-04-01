/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 module for MCP-specific type customizations.
 *
 * <p>Previously registered mixins to hide internal fields from old protocol model types. Those
 * types have been replaced by MCP-local records that inherently exclude hidden fields, so no mixins
 * are needed. This module is retained for future MCP-specific Jackson customizations.
 *
 * <p>Auto-discovered by Spring AI's {@code JsonParser} via the service loader mechanism ({@code
 * META-INF/services/tools.jackson.databind.JacksonModule}), and the same mapper is used by the
 * schema generator (victools).
 */
public class CamundaMcpJackson3Module extends SimpleModule {

  private static final String MODULE_NAME = "CamundaMcpJackson3Module";

  public CamundaMcpJackson3Module() {
    super(MODULE_NAME);
  }
}
