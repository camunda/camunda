/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.ClusterCaptureStatus;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.ClusterTraceExport;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.ExportMetadata;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureBusyException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureConflictException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperationRequestPredicate;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ByteArrayResource;

final class TracesEndpointTest {

  @Test
  void shouldDiscoverDistinctPredicatesWithJsonOnlyPost() {
    // given
    try (final var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          "tracesEndpoint",
          TracesEndpoint.class,
          () -> new TracesEndpoint(mock(ClusterTraceCaptureService.class)));
      context.refresh();

      // when
      final var endpoints =
          new WebEndpointDiscoverer(
                  context,
                  new ConversionServiceParameterValueMapper(),
                  EndpointMediaTypes.DEFAULT,
                  null,
                  null,
                  List.of(),
                  List.of(),
                  List.of())
              .getEndpoints();

      // then - POST consumes JSON only so curl -d without Content-Type fails with 415 instead of
      // silently discarding the request body
      assertThat(
              endpoints.stream()
                  .flatMap(e -> e.getOperations().stream())
                  .map(op -> describe(op.getRequestPredicate()))
                  .collect(Collectors.toUnmodifiableSet()))
          .containsExactlyInAnyOrder(
              "GET traces consumes=none",
              "GET traces/{action} consumes=none",
              "POST traces consumes=json",
              "DELETE traces consumes=none");
    }
  }

  @Test
  void shouldRejectInvalidCaptureConfiguration() {
    // given
    final var service = mock(ClusterTraceCaptureService.class);
    final var endpoint = new TracesEndpoint(service);

    // when / then
    assertThat(endpoint.startCapture("20m", null, null, null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.startCapture("not-a-duration", null, null, null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.startCapture("30s", 1.5, null, null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.startCapture("30s", 0.5, 0, null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.startCapture("30s", 0.5, 10, 999_999).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
  }

  @Test
  void shouldMapServiceGuardExceptionsToAppropriateHttpStatuses() {
    // given
    final var service = mock(ClusterTraceCaptureService.class);
    final var endpoint = new TracesEndpoint(service);

    when(service.startCapture(any()))
        .thenThrow(new TraceCaptureUnavailableException("disabled"))
        .thenThrow(new TraceCaptureConflictException("already active"));
    when(service.exportZip(any())).thenThrow(new TraceCaptureBusyException("busy"));

    // when / then
    assertThat(endpoint.startCapture(null, null, null, null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE);
    assertThat(endpoint.startCapture(null, null, null, null).getStatus())
        .isEqualTo(TracesEndpoint.STATUS_CONFLICT);
    assertThat(endpoint.export("export", null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_TOO_MANY_REQUESTS);
  }

  @Test
  void shouldStartCaptureWithParsedConfigAndClearOnDelete() {
    // given
    final var service = mock(ClusterTraceCaptureService.class);
    final var status = new ClusterCaptureStatus(true, true, 1, 1, 0, 0, Map.of(), Map.of());
    when(service.startCapture(any())).thenReturn(status);
    when(service.clear()).thenReturn(status);
    final var endpoint = new TracesEndpoint(service);

    // when
    final var startResponse = endpoint.startCapture("2m", 0.25, 50, 5000);
    final var deleteResponse = endpoint.clear();

    // then
    assertThat(startResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
    assertThat(deleteResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
    verify(service).startCapture(new TraceCaptureConfig(Duration.ofMinutes(2), 0.25, 50, 5000));
    verify(service).clear();
  }

  @Test
  void shouldValidateExportActionAndTraceIdAndReturnZip() {
    // given
    final var service = mock(ClusterTraceCaptureService.class);
    final var export =
        new ClusterTraceExport(
            new byte[] {1, 2, 3},
            new ExportMetadata(
                "8.9.0", Instant.EPOCH, "0", null, 1, 1, 0, 0, false, Map.of(), Map.of()));
    when(service.exportZip(any())).thenReturn(export);
    final var endpoint = new TracesEndpoint(service);

    // when / then - unknown action or invalid traceId -> 400
    assertThat(endpoint.export("otlp", null).getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.export("export", "not-hex").getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(endpoint.export("export", "00000000000000000000000000000000").getStatus())
        .isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);

    // when / then - uppercase traceId is normalized to lowercase and returns application/zip
    final var zipResponse = endpoint.export("export", "0AF7651916CD43DD8448EB211C80319C");
    assertThat(zipResponse.getStatus()).isEqualTo(WebEndpointResponse.STATUS_OK);
    assertThat(zipResponse.getContentType()).isEqualTo(TracesEndpoint.APPLICATION_ZIP);
    assertThat(zipResponse.getBody()).isInstanceOf(ByteArrayResource.class);
    verify(service).exportZip("0af7651916cd43dd8448eb211c80319c");
  }

  private static String describe(final WebOperationRequestPredicate predicate) {
    final var consumes = predicate.getConsumes().isEmpty() ? "none" : "json";
    return "%s %s consumes=%s".formatted(predicate.getHttpMethod(), predicate.getPath(), consumes);
  }
}
