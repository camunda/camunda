/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled.RestGatewayEnabledCondition;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * The REST API is disabled when {@code camunda.rest.enabled} is set to {@code false}, or when the
 * embedded gateway itself is disabled via {@code camunda.api.enabled} (falling back to the legacy
 * {@code zeebe.broker.gateway.enable} if the new property isn't set) — the same precedence {@code
 * io.camunda.configuration.Api#isEnabled()} uses, so an explicitly-set {@code camunda.api.enabled}
 * always wins over a conflicting legacy value rather than requiring both to agree. By default, all
 * are considered to be set to {@code true} when missing, the REST API is thus enabled by default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnWebApplication
@Conditional(RestGatewayEnabledCondition.class)
public @interface ConditionalOnRestGatewayEnabled {

  final class RestGatewayEnabledCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      final Environment environment = context.getEnvironment();
      final boolean apiEnabled = isApiEnabled(environment);
      final boolean restEnabled =
          environment.getProperty("camunda.rest.enabled", Boolean.class, true);
      if (apiEnabled && restEnabled) {
        return ConditionOutcome.match("REST gateway is enabled");
      }
      return ConditionOutcome.noMatch(
          "REST gateway is disabled (camunda.api.enabled/zeebe.broker.gateway.enable resolved to "
              + apiEnabled
              + ", camunda.rest.enabled resolved to "
              + restEnabled
              + ")");
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
