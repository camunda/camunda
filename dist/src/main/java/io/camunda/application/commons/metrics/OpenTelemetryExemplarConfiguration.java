/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.micrometer.registry.otlp.ExemplarContextProvider;
import io.micrometer.registry.otlp.OtlpExemplarContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.ConditionalOnEnabledMetricsExport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Provides non-lazy OpenTelemetry-backed exemplar providers for Prometheus ({@link SpanContext})
 * and OTLP ({@link ExemplarContextProvider}).
 *
 * <p>Spring Boot's {@code PrometheusExemplarsAutoConfiguration} and {@code
 * OtlpExemplarsAutoConfiguration} wrap {@code ObjectProvider<Tracer>} in a {@code
 * SingletonSupplier} and resolve the {@code Tracer} bean lazily on the first metric observation.
 * Because Camunda starts background Atomix Netty and actor-scheduler threads that record metrics
 * while the main thread still holds Spring's {@code DefaultSingletonBeanRegistry} lock inside
 * {@code @Bean} startup methods (such as {@code Broker.start()}), that lazy {@code BeanFactory}
 * lookup deadlocks multi-node startup. Reading {@link Span#current()} directly preserves metric
 * trace exemplars with zero {@code BeanFactory} access, locks, or per-observation wrapper
 * allocations.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(OpenTelemetryExemplarConfiguration.ExemplarsNotDisabledCondition.class)
@NullMarked
public class OpenTelemetryExemplarConfiguration {

  private static final String EXEMPLARS_INCLUDE_PROPERTY = "management.tracing.exemplars.include";

  @Bean
  @ConditionalOnEnabledMetricsExport("prometheus")
  public static SpanContext openTelemetryPrometheusSpanContext(final Environment environment) {
    final var include = resolveInclude(environment);
    if (include == ExemplarsInclude.ALL) {
      throw new InvalidConfigurationPropertyValueException(
          EXEMPLARS_INCLUDE_PROPERTY,
          "all",
          "Prometheus doesn't support including 'all' traces as exemplars.");
    }
    return new OpenTelemetrySpanContext();
  }

  @Bean
  @ConditionalOnEnabledMetricsExport("otlp")
  public static ExemplarContextProvider openTelemetryOtlpExemplarContextProvider(
      final Environment environment) {
    final var include = resolveInclude(environment);
    return new OpenTelemetryOtlpExemplarContextProvider(include == ExemplarsInclude.ALL);
  }

  private static ExemplarsInclude resolveInclude(final Environment environment) {
    return Binder.get(environment)
        .bind(EXEMPLARS_INCLUDE_PROPERTY, ExemplarsInclude.class)
        .orElse(ExemplarsInclude.SAMPLED_TRACES);
  }

  private static io.opentelemetry.api.trace.SpanContext currentSpanContext() {
    return Span.fromContext(Context.current()).getSpanContext();
  }

  enum ExemplarsInclude {
    ALL,
    NONE,
    SAMPLED_TRACES
  }

  static final class ExemplarsNotDisabledCondition extends NoneNestedConditions {
    ExemplarsNotDisabledCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(value = EXEMPLARS_INCLUDE_PROPERTY, havingValue = "none")
    static final class NoneIncluded {}
  }

  static final class OpenTelemetrySpanContext implements SpanContext {

    @Override
    public @Nullable String getCurrentTraceId() {
      final var context = currentSpanContext();
      return context.isValid() ? context.getTraceId() : null;
    }

    @Override
    public @Nullable String getCurrentSpanId() {
      final var context = currentSpanContext();
      return context.isValid() ? context.getSpanId() : null;
    }

    @Override
    public boolean isCurrentSpanSampled() {
      final var context = currentSpanContext();
      return context.isValid() && context.isSampled();
    }

    @Override
    public void markCurrentSpanAsExemplar() {
      // No-op: OpenTelemetry spans do not track exemplar attachment state.
    }
  }

  static final class OpenTelemetryOtlpExemplarContextProvider implements ExemplarContextProvider {

    private final boolean includeAll;

    OpenTelemetryOtlpExemplarContextProvider(final boolean includeAll) {
      this.includeAll = includeAll;
    }

    @Override
    public @Nullable OtlpExemplarContext getExemplarContext() {
      final var context = currentSpanContext();
      if (context.isValid() && (includeAll || context.isSampled())) {
        return new OtlpExemplarContext(context.getTraceId(), context.getSpanId());
      }
      return null;
    }
  }
}
