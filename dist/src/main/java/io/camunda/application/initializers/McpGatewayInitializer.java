/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Conditionally loads MCP configuration when {@code camunda.mcp.enabled=true}.
 *
 * <p>When enabled, this initializer loads {@code mcp/mcp-gateway.yaml} with higher priority than
 * {@code application.properties} to override the disabled defaults.
 */
public class McpGatewayInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger LOG = LoggerFactory.getLogger(McpGatewayInitializer.class);

  private static final String MCP_ENABLED_PROPERTY = "camunda.mcp.enabled";
  private static final String MCP_CONFIG_FILE = "mcp/mcp-gateway.yaml";

  @Override
  public void initialize(final ConfigurableApplicationContext context) {
    final ConfigurableEnvironment env = context.getEnvironment();
    if (isMcpEnabled(env)) {
      LOG.info("MCP gateway is enabled, loading configuration from {}", MCP_CONFIG_FILE);
      addMcpPropertySource(env);
    }
  }

  private boolean isMcpEnabled(final ConfigurableEnvironment env) {
    return env.getProperty(MCP_ENABLED_PROPERTY, Boolean.class, false);
  }

  private void addMcpPropertySource(final ConfigurableEnvironment env) {
    try {
      final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
      final ClassPathResource resource = new ClassPathResource(MCP_CONFIG_FILE);
      final List<PropertySource<?>> sources = loader.load("mcpConfig", resource);

      // add after system environment to ensure system properties and env vars can override values,
      // but still override the disabled defaults from application.properties
      for (final PropertySource<?> source : sources) {
        env.getPropertySources()
            .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
      }

      LOG.debug("Successfully loaded MCP configuration from {}", MCP_CONFIG_FILE);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Failed to load MCP configuration from " + MCP_CONFIG_FILE, e);
    }
  }
}
