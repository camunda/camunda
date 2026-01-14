/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.condition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabledTest.ConditionalOnAnyHttpGatewayEnabledTestConfiguration.FooService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;

public class ConditionalOnAnyHttpGatewayEnabledTest {

  private WebApplicationContextRunner contextRunner() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ConditionalOnAnyHttpGatewayEnabledTestConfiguration.class);
  }

  @Test
  void enabledWhenRestGatewayImplicitelyEnabled() {
    contextRunner().run(context -> assertThat(context).hasSingleBean(FooService.class));
  }

  @Test
  void enabledWhenRestGatewayExplicitelyEnabled() {
    contextRunner()
        .withPropertyValues("camunda.rest.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(FooService.class));
  }

  @Test
  void enabledWhenMcpGatewayEnabled() {
    contextRunner()
        .withPropertyValues("camunda.rest.enabled=false", "camunda.mcp.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(FooService.class));
  }

  @Test
  void enabledWhenBothGatewaysEnabled() {
    contextRunner()
        .withPropertyValues("camunda.rest.enabled=true", "camunda.mcp.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(FooService.class));
  }

  @Test
  void disabledWhenBothGatewaysDisabled() {
    contextRunner()
        .withPropertyValues("camunda.rest.enabled=false", "camunda.mcp.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(FooService.class));
  }

  @Test
  void disabledWhenZeebeBrokerGatewayDisabled() {
    contextRunner()
        .withPropertyValues(
            "camunda.rest.enabled=true",
            "camunda.mcp.enabled=true",
            "zeebe.broker.gateway.enable=false")
        .run(context -> assertThat(context).doesNotHaveBean(FooService.class));
  }

  @TestConfiguration
  @ConditionalOnAnyHttpGatewayEnabled
  static class ConditionalOnAnyHttpGatewayEnabledTestConfiguration {

    @Bean
    public FooService fooService() {
      return new FooService("test");
    }

    record FooService(String name) {}
  }
}
