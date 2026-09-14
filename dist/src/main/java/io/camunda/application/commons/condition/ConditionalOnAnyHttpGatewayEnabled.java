/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.condition;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled.AnyHttpGatewayEnabledCondition;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(AnyHttpGatewayEnabledCondition.class)
public @interface ConditionalOnAnyHttpGatewayEnabled {

  class AnyHttpGatewayEnabledCondition extends AnyNestedCondition {

    public AnyHttpGatewayEnabledCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    /**
     * The same properties as the nested conditions below, evaluated directly against an {@link
     * Environment}. It exists for the callers that cannot express the question as a bean condition
     * — a context initializer running before any bean definition, and a bean that has to exist
     * either way and only behaves differently — and lives here so that there is one definition of
     * "an HTTP gateway is enabled" rather than several that can drift apart.
     *
     * <p>The nested conditions additionally require a web application context, which this does not
     * model, so the two do not agree everywhere. {@code StandaloneSchemaManager} and {@code
     * StandaloneBackupManager} both run with {@code WebApplicationType.NONE}, and this returns
     * {@code true} in those processes because the properties it reads are simply unset. A caller
     * that can run in one of them has to account for that itself.
     */
    public static boolean isAnyHttpGatewayEnabled(final Environment env) {
      return isApiEnabled(env)
          && (env.getProperty("camunda.rest.enabled", Boolean.class, true)
              || env.getProperty("camunda.mcp.enabled", Boolean.class, false));
    }

    /**
     * Resolves whether the embedded gateway API is enabled with the same precedence {@code
     * io.camunda.configuration.Api#isEnabled()} applies: an explicitly-set {@code
     * camunda.api.enabled} always wins, falling back to the legacy {@code
     * zeebe.broker.gateway.enable} only when the new property isn't set at all — as opposed to a
     * plain AND of both raw values, which would incorrectly require both to agree.
     */
    private static boolean isApiEnabled(final Environment env) {
      if (env.containsProperty("camunda.api.enabled")) {
        return env.getProperty("camunda.api.enabled", Boolean.class, true);
      }
      return env.getProperty("zeebe.broker.gateway.enable", Boolean.class, true);
    }

    @ConditionalOnRestGatewayEnabled
    static class RestGatewayEnabled {}

    @ConditionalOnMcpGatewayEnabled
    static class McpGatewayEnabled {}
  }
}
