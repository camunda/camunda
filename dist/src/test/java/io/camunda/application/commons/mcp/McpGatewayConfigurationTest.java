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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.metrics.ServerRequestObservationConfiguration.LowCardinalityKeyValuesMapper;
import io.camunda.application.commons.service.CamundaServicesConfiguration;
import io.camunda.application.initializers.McpGatewayInitializer;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.util.ContentCachingRequestWrapper;

class McpGatewayConfigurationTest {

  private static final Class<ClusterTools> TEST_TOOL_BEAN_CLASS = ClusterTools.class;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WebApplicationContextRunner contextRunner() {
    WebApplicationContextRunner runner =
        new WebApplicationContextRunner()
            .withInitializer(new McpGatewayInitializer())
            .withBean(MultiTenancyConfiguration.class, MultiTenancyConfiguration::new)
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
    mockBeans.add(ServerRequestObservationContext.class);

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

  @Test
  void tracksNonMcpInObservationTags() {
    contextRunner()
        .withPropertyValues("camunda.mcp.enabled=true")
        .run(
            context -> {
              // given
              final ContentCachingRequestWrapper request =
                  Mockito.mock(ContentCachingRequestWrapper.class);
              final ServerRequestObservationContext observationContext =
                  context.getBean(ServerRequestObservationContext.class);
              Mockito.when(observationContext.getCarrier()).thenReturn(request);
              Mockito.when(request.getServletPath()).thenReturn("/foo");

              final LowCardinalityKeyValuesMapper lowCardinalityKeyValuesMapper =
                  context.getBean(LowCardinalityKeyValuesMapper.class);
              assertThat(lowCardinalityKeyValuesMapper).isNotNull();
              // when
              final KeyValues keyValues = lowCardinalityKeyValuesMapper.apply(observationContext);
              // then
              assertThat(keyValues).isEmpty();
            });
  }

  @ParameterizedTest
  @MethodSource("mcpRequestCases")
  void tracksMcpDetailsInObservationTags(final String requestContent, final String expectedUriTag) {
    contextRunner()
        .withPropertyValues("camunda.mcp.enabled=true")
        .run(
            context -> {
              // given
              final ContentCachingRequestWrapper request =
                  Mockito.mock(ContentCachingRequestWrapper.class);
              final ServerRequestObservationContext observationContext =
                  context.getBean(ServerRequestObservationContext.class);
              Mockito.when(observationContext.getCarrier()).thenReturn(request);
              Mockito.when(request.getMethod()).thenReturn(HttpMethod.POST.name());
              Mockito.when(request.getServletPath()).thenReturn("/mcp");
              Mockito.when(request.getContentAsString())
                  .thenReturn(requestContent == null ? "" : requestContent);

              final ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
              when(objectMapper.readValue(requestContent, McpRequestMetadata.class))
                  .thenAnswer(
                      content -> OBJECT_MAPPER.readValue(requestContent, McpRequestMetadata.class));

              final LowCardinalityKeyValuesMapper lowCardinalityKeyValuesMapper =
                  context.getBean(LowCardinalityKeyValuesMapper.class);
              assertThat(lowCardinalityKeyValuesMapper).isNotNull();
              // when
              final KeyValues keyValues = lowCardinalityKeyValuesMapper.apply(observationContext);
              // then
              assertThat(keyValues).containsExactly(KeyValue.of("uri", expectedUriTag));
            });
  }

  private static Stream<Arguments> mcpRequestCases() {
    return Stream.of(
        Arguments.of(null, "/mcp"),
        Arguments.of("", "/mcp"),
        Arguments.of("invalid-json", "/mcp"),
        Arguments.of("{}", "/mcp"),
        Arguments.of("{\"foo\":\"\"}", "/mcp"),
        Arguments.of("{\"method\":\"\"}", "/mcp"),
        Arguments.of("{\"method\":\"tools/list\"}", "/mcp/tools/list"),
        Arguments.of("{\"method\":\"tools/call\",\"params\":{}}", "/mcp/tools/call"),
        Arguments.of(
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"cluster\"}}",
            "/mcp/tools/call/cluster"),
        Arguments.of(
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"cluster\", \"arguments\":{ \"foo\":\"bar\"}}}",
            "/mcp/tools/call/cluster"));
  }
}
