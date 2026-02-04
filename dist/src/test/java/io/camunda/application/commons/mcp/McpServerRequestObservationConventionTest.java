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
  void tracksMcpDetailsInObservationTags(final String requestContent, final String expectedUriTag) {
    // given
    final ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
    final ServerRequestObservationContext observationContext =
        mock(ServerRequestObservationContext.class);
    when(observationContext.getCarrier()).thenReturn(request);
    when(request.getMethod()).thenReturn(HttpMethod.POST.name());
    when(request.getServletPath()).thenReturn("/mcp");
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
