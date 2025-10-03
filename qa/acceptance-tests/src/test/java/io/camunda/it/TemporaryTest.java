/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

public class TemporaryTest {

  @Test
  void x() throws URISyntaxException, InterruptedException {

    final OpenTelemetry openTelemetry = create("http://localhost:4317");

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(true)
            .build();

    for (int i = 0; i < 3; i++) {
    final Span span = openTelemetry.getTracer("TEST").spanBuilder("test").startSpan();

    try (final Scope scope = span.makeCurrent()) {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process").startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

      client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
    } finally {
      span.end();
    }
    }
    Thread.sleep(5000);
  }

  @Test
  void grpc() throws URISyntaxException, InterruptedException {

    final OpenTelemetry openTelemetry = create("http://localhost:4317");

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(false)
            .build();

    for (int i = 0; i < 3; i++) {
      final Span span = openTelemetry.getTracer("TEST").spanBuilder("test").startSpan();

      try (final Scope scope = span.makeCurrent()) {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process").startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
      } finally {
        span.end();
      }
    }
    Thread.sleep(5000);
  }

  public static OpenTelemetry create(final String exporter) {
    final Resource resource =
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "client-java"));

    final OtlpGrpcSpanExporter otlpExporter =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint(exporter) // Set your OTLP endpoint here
            .build();

    final SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(otlpExporter).build())
            .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal();
  }
}
