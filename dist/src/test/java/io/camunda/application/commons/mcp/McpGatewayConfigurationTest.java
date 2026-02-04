/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.service.CamundaServicesConfiguration;
import io.camunda.application.initializers.McpGatewayInitializer;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;

class McpGatewayConfigurationTest {

  private static final Class<ClusterTools> TEST_TOOL_BEAN_CLASS = ClusterTools.class;

  private WebApplicationContextRunner contextRunner() {
    WebApplicationContextRunner runner =
        new WebApplicationContextRunner()
            .withInitializer(new McpGatewayInitializer())
            .withBean(MultiTenancyConfiguration.class, MultiTenancyConfiguration::new)
            .withBean(ObservationProperties.class, ObservationProperties::new)
            .withUserConfiguration(McpGatewayConfiguration.class);

    final List<Class<?>> mockBeans = new ArrayList<>();
    mockBeans.add(CamundaAuthenticationProvider.class);
    mockBeans.add(BrokerClient.class);

    // create mocks for all declared services in CamundaServicesConfiguration
    Arrays.stream(CamundaServicesConfiguration.class.getDeclaredMethods())
        .filter(method -> AnnotatedElementUtils.hasAnnotation(method, Bean.class))
        .map(Method::getReturnType)
        .forEach(mockBeans::add);

    // add mocks needed for McpGatewayConfiguration
    mockBeans.add(ObjectMapper.class);

    for (final Class<?> mockedBean : mockBeans) {
      runner = addMockedBean(runner, mockedBean);
    }

    return runner;
  }

  private <T> WebApplicationContextRunner addMockedBean(
      final WebApplicationContextRunner runner, final Class<T> type) {
    return runner.withBean(type, () -> mock(type));
  }

  @Test
  void doesNotIncludeMcpToolComponentsByDefault() {
    contextRunner().run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  void includesMcpToolComponentsWhenEnabled() {
    contextRunner()
        .withPropertyValues("camunda.mcp.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  void doesNotIncludeMcpToolComponentsWhenDisabled() {
    contextRunner()
        .withPropertyValues("camunda.mcp.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  void doesNotIncludeMcpToolComponentsWhenBrokerGatewayIsDisabled() {
    contextRunner()
        .withPropertyValues("zeebe.broker.gateway.enable=false", "camunda.mcp.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }
}
