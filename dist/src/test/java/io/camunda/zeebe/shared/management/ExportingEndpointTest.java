/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

final class ExportingEndpointTest {
  @Test
  void pauseFailsIfCallFailsDirectly() {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting()).thenThrow(new RuntimeException());

    // then
    assertThat(endpoint.post(ExportingEndpoint.PAUSE))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @Test
  void pauseFailsIfCallReturnsFailedFuture() {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    // then
    assertThat(endpoint.post(ExportingEndpoint.PAUSE))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @Test
  void pauseCanSucceed() {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // then
    assertThat(endpoint.post(ExportingEndpoint.PAUSE))
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
  }
}
