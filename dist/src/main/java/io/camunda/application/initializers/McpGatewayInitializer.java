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
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Conditionally loads MCP configuration when {@code camunda.mcp.enabled=true}. This allows enabling
 * the Spring AI MCP server without relying on Spring profiles.
 *
 * <p>When enabled, this initializer loads {@code mcp/mcp-gateway.yaml} with higher priority than
 * {@code application.properties} to override the disabled defaults.
 */
public class McpGatewayInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  private static final Logger LOG = LoggerFactory.getLogger(McpGatewayInitializer.class);

  private static final String MCP_ENABLED_PROPERTY = "camunda.mcp.enabled";
  private static final String SPRING_AI_MCP_ENABLED_PROPERTY = "spring.ai.mcp.server.enabled";

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

      addBeforeApplicationPropertiesSource(env, sources);

      LOG.debug("Successfully loaded MCP configuration from {}", MCP_CONFIG_FILE);
    } catch (final IOException e) {
      throw new IllegalStateException(
          "Failed to load MCP configuration from " + MCP_CONFIG_FILE, e);
    }
  }

  private void addBeforeApplicationPropertiesSource(
      final ConfigurableEnvironment env, final List<PropertySource<?>> sources) {
    final var propertySources = env.getPropertySources();
    final var applicationPropertiesSource =
        findMcpDisabledApplicationPropertiesSource(propertySources);

    for (final var source : sources) {
      propertySources.addBefore(applicationPropertiesSource.getName(), source);
    }
  }

  /**
   * Returns the property source disabling the Spring AI MCP server defaults
   * (application.properties). This is needed as we need to insert the MCP configuration with a
   * higher priority than the source containing these defaults.
   *
   * <p>The check on the name is important as a {@code ConfigurationPropertySourcesPropertySource}
   * may be present with a high proiority, wrapping the actual application.properties source.
   */
  private PropertySource<?> findMcpDisabledApplicationPropertiesSource(
      final MutablePropertySources propertySources) {
    return propertySources.stream()
        .filter(
            p ->
                p.getName().contains("application.properties")
                    && p.containsProperty(SPRING_AI_MCP_ENABLED_PROPERTY)
                    && "false".equals(p.getProperty(SPRING_AI_MCP_ENABLED_PROPERTY)))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not find application.properties source initializing property %s=false"
                        .formatted(SPRING_AI_MCP_ENABLED_PROPERTY)));
  }
}
