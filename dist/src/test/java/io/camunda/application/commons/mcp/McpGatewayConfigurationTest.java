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

import io.camunda.application.commons.service.CamundaServicesConfiguration;
import io.camunda.application.initializers.McpGatewayInitializer;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import jakarta.servlet.ServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.databind.json.JsonMapper;

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

    // mock JsonMapper used in MCP observation convention
    mockBeans.add(JsonMapper.class);

    // mock qualified JsonMapper dependency used in MCP transports
    runner = runner.withBean("mcpServerJsonMapper", JsonMapper.class, () -> mock(JsonMapper.class));

    for (final Class<?> mockedBean : mockBeans) {
      runner = addMockedBean(runner, mockedBean);
    }

    return runner;
  }

  private <T> WebApplicationContextRunner addMockedBean(
      final WebApplicationContextRunner runner, final Class<T> type) {
    return runner.withBean(type, () -> mock(type));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/mcp/cluster",
        "/mcp/processes",
        "/physical-tenants/tenant-a/mcp/cluster",
        "/physical-tenants/tenant-a/mcp/processes",
        "/physical-tenants/tenant-a/mcp/cluster/sub",
      })
  void shouldWrapMcpRequestsInContentCachingRequestWrapper(final String servletPath)
      throws Exception {
    // given
    final OncePerRequestFilter filter =
        new McpGatewayConfiguration().mcpContentCachingFilter().getFilter();
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath(servletPath);
    final List<ServletRequest> captured = new ArrayList<>();

    // when
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> captured.add(req));

    // then
    assertThat(captured).hasSize(1);
    assertThat(captured.getFirst()).isInstanceOf(ContentCachingRequestWrapper.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/mcp",
        "/v2/process-instances",
        "/mcpfoo",
        "/physical-tenants/tenant-a/mcp",
        "/physical-tenants/tenant-a/v2/something",
        "/physical-tenants/tenant-a/mcpfoo",
        "/physical-tenants//mcp",
      })
  void shouldNotWrapNonMcpRequestsInContentCachingRequestWrapper(final String servletPath)
      throws Exception {
    // given
    final OncePerRequestFilter filter =
        new McpGatewayConfiguration().mcpContentCachingFilter().getFilter();
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.setServletPath(servletPath);
    final List<ServletRequest> captured = new ArrayList<>();

    // when
    filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> captured.add(req));

    // then
    assertThat(captured).hasSize(1);
    assertThat(captured.getFirst()).isNotInstanceOf(ContentCachingRequestWrapper.class);
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
