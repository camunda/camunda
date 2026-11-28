/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.ClusterCaptureStatus;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureBusyException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureConflictException;
import io.camunda.zeebe.shared.management.tracing.ClusterTraceCaptureService.TraceCaptureUnavailableException;
import io.opentelemetry.api.trace.TraceId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Actuator endpoint for on-demand, cluster-wide OpenTelemetry trace capture and export without an
 * external collector.
 *
 * <ul>
 *   <li>{@code POST /actuator/traces} — starts a time-boxed capture window across all cluster
 *       members (brokers and gateways). Requires {@code Content-Type: application/json} (pass
 *       {@code {}} for defaults) so forgotten content-type headers fail loudly with 415 instead of
 *       silently ignoring capture options.
 *   <li>{@code GET /actuator/traces} — returns cluster-wide capture status and span counts.
 *   <li>{@code GET /actuator/traces/export[?traceId=...]} — downloads a ZIP archive containing
 *       merged standard OTLP/JSON ({@code traces.otlp.json}) and {@code metadata.json}.
 *   <li>{@code DELETE /actuator/traces} — stops any active capture and clears buffered spans across
 *       all cluster members.
 * </ul>
 */
@WebEndpoint(id = "traces")
@NullMarked
public final class TracesEndpoint {

  static final String ACTION_EXPORT = "export";
  static final int STATUS_CONFLICT = 409;
  static final MimeType APPLICATION_ZIP = MimeType.valueOf("application/zip");

  private final ClusterTraceCaptureService service;

  public TracesEndpoint(final ClusterTraceCaptureService service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  @ReadOperation
  public WebEndpointResponse<ClusterCaptureStatus> status() {
    return new WebEndpointResponse<>(
        service.status(), WebEndpointResponse.STATUS_OK, MimeTypeUtils.APPLICATION_JSON);
  }

  @ReadOperation(produces = {"application/zip", "application/json"})
  public WebEndpointResponse<?> export(
      final @Selector String action, final @Nullable String traceId) {
    if (!ACTION_EXPORT.equals(action)) {
      return errorResponse(
          WebEndpointResponse.STATUS_BAD_REQUEST,
          "Unknown action '%s'; expected '%s'".formatted(action, ACTION_EXPORT));
    }

    final String normalizedTraceId;
    try {
      normalizedTraceId = normalizeAndValidateTraceId(traceId);
    } catch (final IllegalArgumentException e) {
      return errorResponse(WebEndpointResponse.STATUS_BAD_REQUEST, e.getMessage());
    }

    try {
      final var export = service.exportZip(normalizedTraceId);
      return new WebEndpointResponse<>(
          new ByteArrayResource(export.zipBytes()), WebEndpointResponse.STATUS_OK, APPLICATION_ZIP);
    } catch (final TraceCaptureBusyException e) {
      return errorResponse(WebEndpointResponse.STATUS_TOO_MANY_REQUESTS, e.getMessage());
    }
  }

  @WriteOperation
  public WebEndpointResponse<?> startCapture(
      final @Nullable String duration,
      final @Nullable Double samplingProbability,
      final @Nullable Integer maxTracesPerSecond,
      final @Nullable Integer maxSpansPerNode) {
    final TraceCaptureConfig config;
    try {
      config =
          TraceCaptureConfig.fromRequest(
              duration, samplingProbability, maxTracesPerSecond, maxSpansPerNode);
    } catch (final IllegalArgumentException e) {
      return errorResponse(WebEndpointResponse.STATUS_BAD_REQUEST, e.getMessage());
    }

    try {
      return new WebEndpointResponse<>(
          service.startCapture(config),
          WebEndpointResponse.STATUS_OK,
          MimeTypeUtils.APPLICATION_JSON);
    } catch (final TraceCaptureUnavailableException e) {
      return errorResponse(WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE, e.getMessage());
    } catch (final TraceCaptureConflictException e) {
      return errorResponse(STATUS_CONFLICT, e.getMessage());
    }
  }

  @DeleteOperation
  public WebEndpointResponse<ClusterCaptureStatus> clear() {
    return new WebEndpointResponse<>(
        service.clear(), WebEndpointResponse.STATUS_OK, MimeTypeUtils.APPLICATION_JSON);
  }

  private static @Nullable String normalizeAndValidateTraceId(final @Nullable String traceId) {
    if (traceId == null || traceId.isBlank()) {
      return null;
    }
    final var normalized = traceId.trim().toLowerCase(Locale.ROOT);
    if (!TraceId.isValid(normalized)) {
      throw new IllegalArgumentException(
          "Invalid traceId '%s': expected 32 hexadecimal characters (not all zeros)"
              .formatted(traceId));
    }
    return normalized;
  }

  private static WebEndpointResponse<Map<String, String>> errorResponse(
      final int status, final @Nullable String message) {
    return new WebEndpointResponse<>(
        Map.of("error", message != null ? message : "Request failed"),
        status,
        MimeTypeUtils.APPLICATION_JSON);
  }
}
