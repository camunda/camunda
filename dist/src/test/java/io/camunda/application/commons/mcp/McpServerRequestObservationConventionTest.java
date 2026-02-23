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
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.util.ContentCachingRequestWrapper;

class McpServerRequestObservationConventionTest {

  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void tracksNonMcpInObservationTags() {
    // given
    final ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
    final ServerRequestObservationContext observationContext =
        mock(ServerRequestObservationContext.class);
    when(request.getMethod()).thenReturn(HttpMethod.POST.name());
    when(request.getServletPath()).thenReturn("/foo");
    when(observationContext.getCarrier()).thenReturn(request);

    // when
    final KeyValues keyValues =
        McpServerRequestObservationConvention.getMcpRequestLowCardinalityValues(
            observationContext, OBJECT_MAPPER);
    // then
    assertThat(keyValues).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("mcpRequestCases")
  void tracksMcpDetailsInObservationTags(
      final String servletPath, final String requestContent, final String expectedUriTag) {
    // given
    final ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
    final ServerRequestObservationContext observationContext =
        mock(ServerRequestObservationContext.class);
    when(observationContext.getCarrier()).thenReturn(request);
    when(request.getMethod()).thenReturn(HttpMethod.POST.name());
    when(request.getServletPath()).thenReturn(servletPath);
    when(request.getContentAsString()).thenReturn(requestContent == null ? "" : requestContent);

    // when
    final KeyValues keyValues =
        McpServerRequestObservationConvention.getMcpRequestLowCardinalityValues(
            observationContext, OBJECT_MAPPER);
    // then
    assertThat(keyValues).containsExactly(KeyValue.of("uri", expectedUriTag));
  }

  private static Stream<Arguments> mcpRequestCases() {
    return Stream.of(
        // /mcp/cluster path
        Arguments.of("/mcp/cluster", null, "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "", "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "invalid-json", "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "{}", "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "{\"foo\":\"\"}", "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "{\"method\":\"\"}", "/mcp/cluster"),
        Arguments.of("/mcp/cluster", "{\"method\":\"tools/list\"}", "/mcp/cluster/tools/list"),
        Arguments.of(
            "/mcp/cluster", "{\"method\":\"tools/call\",\"params\":{}}", "/mcp/cluster/tools/call"),
        Arguments.of(
            "/mcp/cluster",
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"cluster\"}}",
            "/mcp/cluster/tools/call/cluster"),
        Arguments.of(
            "/mcp/cluster",
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"cluster\", \"arguments\":{ \"foo\":\"bar\"}}}",
            "/mcp/cluster/tools/call/cluster"),
        // /mcp/processes path
        Arguments.of("/mcp/processes", null, "/mcp/processes"),
        Arguments.of("/mcp/processes", "{\"method\":\"tools/list\"}", "/mcp/processes/tools/list"),
        Arguments.of(
            "/mcp/processes",
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"start\"}}",
            "/mcp/processes/tools/call/start"),
        // /mcp/business path
        Arguments.of("/mcp/business", null, "/mcp/business"),
        Arguments.of("/mcp/business", "{\"method\":\"tools/list\"}", "/mcp/business/tools/list"),
        // nested path like /mcp/processes/tenant/{tenantId}
        Arguments.of("/mcp/processes/tenant/tenant1", null, "/mcp/processes/tenant/tenant1"),
        Arguments.of(
            "/mcp/processes/tenant/tenant1",
            "{\"method\":\"tools/call\",\"params\":{\"name\":\"deploy\"}}",
            "/mcp/processes/tenant/tenant1/tools/call/deploy"));
  }
}
