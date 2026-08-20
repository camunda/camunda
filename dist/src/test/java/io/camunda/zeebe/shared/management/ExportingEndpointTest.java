/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

final class ExportingEndpointTest {
  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailsIfCallFailsDirectly(final String operation) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID)).thenThrow(new RuntimeException());
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID)).thenThrow(new RuntimeException());

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailIfCallReturnsFailedFuture(final String operation) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeCanSucceed(final String operation) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @MethodSource("exceptionSource")
  void shouldReturnResponseCorrectlyWhenExceptionIsThrown(
      final String operation, final String message) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service);
    final var exception = new RuntimeException(message);

    // when
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(exception));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new CompletionException(exception)));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus))
        .satisfies(
            resp -> {
              if (message != null) {
                assertThat(resp.getBody())
                    .isInstanceOf(String.class)
                    .asString()
                    .contains(exception.getMessage());
              } else {
                assertThat(resp.getBody()).isNull();
              }
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldApplyToEveryPhysicalTenantWithoutParameter(final String operation) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenantb", "tenanta"));
    when(service.pauseExporting(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(service.resumeExporting(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(operation, false, null);

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    final var inOrder = inOrder(service);
    for (final var tenant : List.of("tenanta", "tenantb")) {
      if (ExportingEndpoint.PAUSE.equals(operation)) {
        inOrder.verify(service).pauseExporting(tenant);
      } else {
        inOrder.verify(service).resumeExporting(tenant);
      }
    }
    verifyNoMoreInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldApplyOnlyToRequestedPhysicalTenant(final String operation) {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenanta", "tenantb"));
    when(service.pauseExporting(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(service.resumeExporting(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(operation, false, "tenantb");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    if (ExportingEndpoint.PAUSE.equals(operation)) {
      verify(service).pauseExporting("tenantb");
    } else {
      verify(service).resumeExporting("tenantb");
    }
    verifyNoMoreInteractions(service);
  }

  @Test
  void shouldAttemptEveryPhysicalTenantWhenOneThrowsBeforeReturningAFuture() {
    // given a tenant whose topology check fails, which the broker client raises synchronously
    // rather than through a failed future
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenanta", "tenantb"));
    when(service.pauseExporting("tenanta"))
        .thenThrow(new IncompleteTopologyException("tenanta has no leader"));
    when(service.pauseExporting("tenantb")).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, null);

    // then the remaining tenant is still attempted, so the operator is not left with a mixed
    // cluster that the response says nothing about
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).asString().contains("tenanta has no leader");
    verify(service).pauseExporting("tenantb");
  }

  @Test
  void shouldAttemptEveryPhysicalTenantEvenWhenOneFails() {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenanta", "tenantb"));
    when(service.pauseExporting("tenanta"))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("tenanta is down")));
    when(service.pauseExporting("tenantb")).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, null);

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).asString().contains("tenanta is down");
    verify(service).pauseExporting("tenantb");
  }

  @Test
  void shouldRejectUnknownPhysicalTenant() {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenanta"));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, "tenantz");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(response.getBody())
        .asString()
        .isEqualTo("Unknown physical tenant 'tenantz'. Configured physical tenants: [tenanta].");
    verifyNoInteractions(service);
  }

  @Test
  void shouldTreatBlankPhysicalTenantAsEveryTenant() {
    // given
    final var service = mock(ExportingRequestBroadcaster.class);
    final var endpoint = new ExportingEndpoint(service, () -> Set.of("tenanta", "tenantb"));
    when(service.pauseExporting(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, "  ");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    verify(service).pauseExporting("tenanta");
    verify(service).pauseExporting("tenantb");
  }

  private static Stream<Arguments> exceptionSource() {
    return Stream.of(ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME)
        .flatMap(
            operation ->
                Stream.of("expected error", null).map(str -> Arguments.of(operation, str)));
  }
}
