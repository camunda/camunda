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
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The MCP gateway is disabled when the embedded gateway itself is disabled — via {@code
 * camunda.api.enabled}, falling back to the legacy {@code zeebe.broker.gateway.enable} if the new
 * property isn't set, with an explicitly-set {@code camunda.api.enabled} always winning over a
 * conflicting legacy value rather than requiring both to agree (the same precedence {@code
 * io.camunda.configuration.Api#isEnabled()} uses) — or when {@code camunda.mcp.enabled} is not set
 * to {@code true}. By default, {@code camunda.mcp.enabled} is considered to be set to {@code false}
 * when missing, the MCP gateway is thus disabled by default.
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

    @Conditional(ZeebeBrokerGatewayEnabledCondition.class)
    static class ZeebeBrokerGatewayEnabled {}

    @ConditionalOnProperty(
        name = {"camunda.mcp.enabled"},
        havingValue = "true")
    static class McpGatewayEnabled {}
  }

  final class ZeebeBrokerGatewayEnabledCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment environment = context.getEnvironment();
      final boolean apiEnabled = isApiEnabled(environment);
      return apiEnabled
          ? ConditionOutcome.match("embedded gateway is enabled")
          : ConditionOutcome.noMatch(
              "embedded gateway is disabled (camunda.api.enabled/zeebe.broker.gateway.enable"
                  + " resolved to false)");
    }

    /**
     * Resolves whether the embedded gateway API is enabled with the same precedence {@code
     * io.camunda.configuration.Api#isEnabled()} applies: an explicitly-set {@code
     * camunda.api.enabled} always wins, falling back to the legacy {@code
     * zeebe.broker.gateway.enable} only when the new property isn't set at all — as opposed to
     * {@code @ConditionalOnProperty}'s multi-name AND, which would incorrectly require both to
     * agree.
     */
    private static boolean isApiEnabled(final Environment environment) {
      if (environment.containsProperty("camunda.api.enabled")) {
        return environment.getProperty("camunda.api.enabled", Boolean.class, true);
      }
      return environment.getProperty("zeebe.broker.gateway.enable", Boolean.class, true);
    }
  }
}
