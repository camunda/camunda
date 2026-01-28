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

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(AnyHttpGatewayEnabledCondition.class)
public @interface ConditionalOnAnyHttpGatewayEnabled {

  class AnyHttpGatewayEnabledCondition extends AnyNestedCondition {

    public AnyHttpGatewayEnabledCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnRestGatewayEnabled
    static class RestGatewayEnabled {}

    @ConditionalOnMcpGatewayEnabled
    static class McpGatewayEnabled {}
  }
}
