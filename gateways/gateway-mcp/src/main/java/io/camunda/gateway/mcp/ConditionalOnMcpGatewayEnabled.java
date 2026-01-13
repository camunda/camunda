/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled.McpGatewayEnabledCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Conditional;

/**
 * The MCP gateway is disabled when either the {@code zeebe.broker.gateway.enable} or {@code
 * camunda.mcp.enabled} property is set to {@code false}. By default, the latter is considered to be
 * set to {@code false} when missing, the MCP gateway is thus disabled by default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnWebApplication
@Conditional(McpGatewayEnabledCondition.class)
public @interface ConditionalOnMcpGatewayEnabled {

  class McpGatewayEnabledCondition extends AllNestedConditions {

    public McpGatewayEnabledCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(
        name = {"zeebe.broker.gateway.enable"},
        havingValue = "true",
        matchIfMissing = true)
    static class ZeebeBrokerGatewayEnabled {}

    @ConditionalOnProperty(
        name = {"camunda.mcp.enabled"},
        havingValue = "true")
    static class McpGatewayEnabled {}
  }
}
