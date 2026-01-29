/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TemporaryTest {

  @Test
  void x() throws URISyntaxException, InterruptedException {

    final OpenTelemetry openTelemetry = create("http://localhost:4317", "client-java");

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

    final OpenTelemetry openTelemetry = create("http://localhost:4317", "client-java");

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

  public static OpenTelemetry create(final String exporter, final String serviceName) {
    final Resource resource =
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName));

    final OtlpGrpcSpanExporter otlpExporter =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint(exporter) // Set your OTLP endpoint here
            .build();

    final SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(otlpExporter))
            .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(sdkTracerProvider)
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal();
  }

  @Test
  void runDemo() throws Exception {
    final OpenTelemetry openTelemetry1 = create("http://localhost:4317", "adnumac-webshop");

    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(true)
            .build();

    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/tracing.bpmn")
        .send()
        .join();

    final Span newOrderSpan =
        openTelemetry1.getTracer("com.adnumac.webshop").spanBuilder("newOrder").startSpan();

    try (final Scope scope = newOrderSpan.makeCurrent()) {
      // Create traceparent header
      final Map<String, String> carrier = new HashMap<>();
      W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
      final String traceparent = carrier.get("traceparent");
      final var instance =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("tracingDemo")
              .latestVersion()
              .variable("traceparent", traceparent)
              .send()
              .join();
      newOrderSpan.setAttribute("camunda.process.instance.key", instance.getProcessInstanceKey());
    } finally {
      newOrderSpan.end();
    }

    Thread.sleep(5000);
  }

  @Test
  void checkStockWorker() throws URISyntaxException {
    final OpenTelemetry openTelemetry2 = create("http://localhost:4317", "adnumac-stock-service");
    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(true)
            .build();
    client
        .newWorker()
        .jobType("checkStock")
        .handler(
            (jobClient, job) -> {
              System.out.println("Working job: checkStock");
              final Span span =
                  openTelemetry2
                      .getTracer("com.adnumac.webshop")
                      .spanBuilder("checkStock")
                      .setParent(getParentContext(job))
                      .startSpan();
              span.setAttribute("camunda.process.instance.key", job.getProcessInstanceKey());
              try (final Scope scope = span.makeCurrent()) {
                jobClient.newCompleteCommand(job.getKey()).send().join();
              } finally {
                span.end();
              }
            })
        .open();

    while (true) {}
  }

  @Test
  void shipProductWorker() throws URISyntaxException {
    final OpenTelemetry openTelemetry3 =
        create("http://localhost:4317", "adnumac-shipping-service");
    final CamundaClient client =
        CamundaClient.newClientBuilder()
            .restAddress(new URI("http://localhost:8080"))
            .grpcAddress(new URI("http://localhost:26500"))
            .preferRestOverGrpc(true)
            .build();
    client
        .newWorker()
        .jobType("shipProduct")
        .handler(
            (jobClient, job) -> {
              System.out.println("Working job: shipProduct");
              final Span span =
                  openTelemetry3
                      .getTracer("com.adnumac.webshop")
                      .spanBuilder("shipProduct")
                      .setParent(getParentContext(job))
                      .startSpan();
              span.setAttribute("camunda.process.instance.key", job.getProcessInstanceKey());
              try (final Scope scope = span.makeCurrent()) {
                jobClient.newCompleteCommand(job.getKey()).send().join();
              } finally {
                span.end();
              }
            })
        .open();

    while (true) {}
  }

  private static Context getParentContext(final ActivatedJob job) {
    final String traceparent = (String) job.getVariablesAsMap().get("traceparent");

    final Map<String, String> carrier = Map.of("traceparent", traceparent);
    final Context extractedContext =
        W3CTraceContextPropagator.getInstance()
            .extract(
                Context.current(),
                carrier,
                new TextMapGetter<>() {
                  @Override
                  public Iterable<String> keys(final Map<String, String> c) {
                    return c.keySet();
                  }

                  @Override
                  public String get(final Map<String, String> c, final String key) {
                    return c.get(key);
                  }
                });
    return extractedContext;
  }
}
