/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.metrics.OpenTelemetryExemplarConfiguration.OpenTelemetryOtlpExemplarContextProvider;
import io.camunda.application.commons.metrics.OpenTelemetryExemplarConfiguration.OpenTelemetrySpanContext;
import io.micrometer.registry.otlp.ExemplarContextProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

final class OpenTelemetryExemplarConfigurationTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String SPAN_ID = "b7ad6b7169203331";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(OpenTelemetryExemplarConfiguration.class);

  @Test
  void shouldReadCurrentOpenTelemetrySpanDirectlyForPrometheus() {
    // given
    final SpanContext spanContext = new OpenTelemetrySpanContext();

    // then - untraced thread
    assertThat(spanContext.isCurrentSpanSampled()).isFalse();
    assertThat(spanContext.getCurrentTraceId()).isNull();
    assertThat(spanContext.getCurrentSpanId()).isNull();

    // when - sampled OpenTelemetry span is current
    final var sampled =
        Span.wrap(
            io.opentelemetry.api.trace.SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
    try (final var ignored = sampled.makeCurrent()) {
      assertThat(spanContext.isCurrentSpanSampled()).isTrue();
      assertThat(spanContext.getCurrentTraceId()).isEqualTo(TRACE_ID);
      assertThat(spanContext.getCurrentSpanId()).isEqualTo(SPAN_ID);
    }

    // when - unsampled OpenTelemetry span is current
    final var unsampled =
        Span.wrap(
            io.opentelemetry.api.trace.SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));
    try (final var ignored = unsampled.makeCurrent()) {
      assertThat(spanContext.isCurrentSpanSampled()).isFalse();
      assertThat(spanContext.getCurrentTraceId()).isEqualTo(TRACE_ID);
      assertThat(spanContext.getCurrentSpanId()).isEqualTo(SPAN_ID);
    }
  }

  @Test
  void shouldReadCurrentOpenTelemetrySpanDirectlyForOtlp() {
    // given
    final ExemplarContextProvider sampledOnly = new OpenTelemetryOtlpExemplarContextProvider(false);
    final ExemplarContextProvider includeAll = new OpenTelemetryOtlpExemplarContextProvider(true);

    // then - untraced thread
    assertThat(sampledOnly.getExemplarContext()).isNull();
    assertThat(includeAll.getExemplarContext()).isNull();

    // when - sampled span
    final var sampled =
        Span.wrap(
            io.opentelemetry.api.trace.SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
    try (final var ignored = sampled.makeCurrent()) {
      final var ctx = sampledOnly.getExemplarContext();
      assertThat(ctx).isNotNull();
      assertThat(ctx.getTraceId()).isEqualTo(TRACE_ID);
      assertThat(ctx.getSpanId()).isEqualTo(SPAN_ID);

      final var allCtx = includeAll.getExemplarContext();
      assertThat(allCtx).isNotNull();
      assertThat(allCtx.getTraceId()).isEqualTo(TRACE_ID);
      assertThat(allCtx.getSpanId()).isEqualTo(SPAN_ID);
    }

    // when - unsampled span
    final var unsampled =
        Span.wrap(
            io.opentelemetry.api.trace.SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));
    try (final var ignored = unsampled.makeCurrent()) {
      assertThat(sampledOnly.getExemplarContext()).isNull();

      final var allCtx = includeAll.getExemplarContext();
      assertThat(allCtx).isNotNull();
      assertThat(allCtx.getTraceId()).isEqualTo(TRACE_ID);
      assertThat(allCtx.getSpanId()).isEqualTo(SPAN_ID);
    }
  }

  @Test
  void shouldRegisterPrometheusSpanContextByDefaultAndWithRelaxedBinding() {
    contextRunner
        .withPropertyValues(
            "management.prometheus.metrics.export.enabled=true",
            "management.otlp.metrics.export.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .hasSingleBean(SpanContext.class)
                  .getBean(SpanContext.class)
                  .isInstanceOf(OpenTelemetrySpanContext.class);
              assertThat(context).doesNotHaveBean(ExemplarContextProvider.class);
            });

    contextRunner
        .withPropertyValues(
            "management.prometheus.metrics.export.enabled=true",
            "management.otlp.metrics.export.enabled=false",
            "management.tracing.exemplars.include=SAMPLED_TRACES")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(SpanContext.class)
                    .getBean(SpanContext.class)
                    .isInstanceOf(OpenTelemetrySpanContext.class));
  }

  @Test
  void shouldRejectIncludeAllWhenPrometheusExportIsEnabled() {
    contextRunner
        .withPropertyValues(
            "management.prometheus.metrics.export.enabled=true",
            "management.otlp.metrics.export.enabled=false",
            "management.tracing.exemplars.include=all")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(InvalidConfigurationPropertyValueException.class));
  }

  @Test
  void shouldRegisterOtlpExemplarContextProviderWhenOtlpExportIsEnabled() {
    contextRunner
        .withPropertyValues(
            "management.prometheus.metrics.export.enabled=false",
            "management.otlp.metrics.export.enabled=true",
            "management.tracing.exemplars.include=all")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SpanContext.class);
              assertThat(context)
                  .hasSingleBean(ExemplarContextProvider.class)
                  .getBean(ExemplarContextProvider.class)
                  .isInstanceOf(OpenTelemetryOtlpExemplarContextProvider.class);
            });
  }

  @Test
  void shouldBackOffBothProvidersWhenExemplarsAreDisabled() {
    contextRunner
        .withPropertyValues(
            "management.prometheus.metrics.export.enabled=true",
            "management.otlp.metrics.export.enabled=true",
            "management.tracing.exemplars.include=none")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(SpanContext.class);
              assertThat(context).doesNotHaveBean(ExemplarContextProvider.class);
            });
  }
}
