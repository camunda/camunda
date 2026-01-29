/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

public final class OtelBootstrap {

  private OtelBootstrap() {}

  public static OpenTelemetry initGlobalOpenTelemetry() {
    final Resource resource =
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "camunda"));

    final OtlpGrpcSpanExporter spanExporter =
        OtlpGrpcSpanExporter.builder()
            //            .setEndpoint("http://localhost:4317") // optional; otherwise uses
            // defaults/env
            .build();

    final SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .build();

    final OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

    GlobalOpenTelemetry.set(openTelemetry);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  tracerProvider.close(); // flushes spans
                }));

    return openTelemetry;
  }

  public static Tracer tracer() {
    return GlobalOpenTelemetry.getTracer("io.camunda.zeebe.engine");
  }
}
