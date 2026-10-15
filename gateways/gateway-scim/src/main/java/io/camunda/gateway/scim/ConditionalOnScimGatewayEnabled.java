/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim;

import io.camunda.gateway.scim.ConditionalOnScimGatewayEnabled.ScimGatewayEnabledCondition;
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
 * The SCIM gateway is disabled when the embedded gateway itself is disabled ({@code
 * camunda.api.enabled}, falling back to the legacy {@code zeebe.broker.gateway.enable}) or when
 * {@code camunda.scim.enabled} is not set to {@code true}. It is thus disabled by default.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnWebApplication
@Conditional(ScimGatewayEnabledCondition.class)
public @interface ConditionalOnScimGatewayEnabled {

  class ScimGatewayEnabledCondition extends AllNestedConditions {

    public ScimGatewayEnabledCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @Conditional(EmbeddedGatewayEnabledCondition.class)
    static class EmbeddedGatewayEnabled {}

    @ConditionalOnProperty(name = "camunda.scim.enabled", havingValue = "true")
    static class ScimEnabled {}
  }

  final class EmbeddedGatewayEnabledCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
        final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return isApiEnabled(context.getEnvironment())
          ? ConditionOutcome.match("embedded gateway is enabled")
          : ConditionOutcome.noMatch("embedded gateway is disabled");
    }

    /** An explicitly set {@code camunda.api.enabled} wins over the legacy property. */
    private static boolean isApiEnabled(final Environment environment) {
      if (environment.containsProperty("camunda.api.enabled")) {
        return environment.getProperty("camunda.api.enabled", Boolean.class, true);
      }
      return environment.getProperty("zeebe.broker.gateway.enable", Boolean.class, true);
    }
  }
}
