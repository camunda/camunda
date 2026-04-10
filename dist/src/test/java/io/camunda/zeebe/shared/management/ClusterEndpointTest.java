/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ClusterEndpointTest {

  @Test
  void shouldAllowKnownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(
            Map.of(
                "dryRun", new String[] {"true"},
                "force", new String[] {"false"},
                "replicationFactor", new String[] {"3"}));

    // when - then
    assertThatCode(() -> endpoint.validateRequestParameters(request)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUnknownQueryParameter() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("dryRun", new String[] {"true"}, "unknown", new String[] {"x"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): unknown");
  }

  @Test
  void shouldRejectAndSortMultipleUnknownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("x", new String[] {"1"}, "y", new String[] {"2"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): x, y");
  }

  private ClusterEndpoint createEndpoint() {
    return new ClusterEndpoint(mock(ClusterConfigurationManagementRequestSender.class));
  }
}
