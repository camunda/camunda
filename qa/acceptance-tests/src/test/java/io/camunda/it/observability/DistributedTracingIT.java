/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Verifies that a request traced by a client produces one connected trace through the gateway (gRPC
 * and REST) and broker client.
 */
@ZeebeIntegration
final class DistributedTracingIT {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String CLIENT_SPAN_ID = "b7ad6b7169203331";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + CLIENT_SPAN_ID + "-01";
  private static final String BROKER_CLIENT_SPAN_NAME =
      "zeebe.broker_client PROCESS_INSTANCE_CREATION CREATE";

  private static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();

  // Registered with its concrete type so Spring's SpanProcessor lookup does not fail on the test
  // harness's resolvable-dependency registration.
  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBean(
              "testSpanProcessor",
              (SimpleSpanProcessor) SimpleSpanProcessor.create(SPANS),
              SimpleSpanProcessor.class);

  private static String processId;

  @BeforeAll
  static void deployProcess() {
    processId = "tracing-" + UUID.randomUUID();
    try (final var client = BROKER.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("task", t -> t.zeebeJobType("tracing-task"))
                  .endEvent()
                  .done(),
              "process.bpmn")
          .send()
          .join();
    }
  }

  @BeforeEach
  void resetSpans() {
    SPANS.reset();
  }

  @Test
  void shouldTraceGrpcRequestThroughGatewayAndBrokerClient() {
    // when
    try (final var client =
        BROKER.newClientBuilder().withInterceptors(new TraceparentClientInterceptor()).build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    // then - connected chain from broker client -> gRPC gateway -> caller
    final var chain = awaitChainFromBrokerClientSpan();
    assertThat(chain)
        .extracting(SpanData::getName, SpanData::getKind)
        .containsExactly(
            tuple(BROKER_CLIENT_SPAN_NAME, SpanKind.CLIENT),
            tuple("gateway_protocol.Gateway/CreateProcessInstance", SpanKind.SERVER));
    assertChainIsConnected(chain);

    final var brokerClient = chain.getFirst();
    assertThat(brokerClient.getAttributes().get(AttributeKey.longKey("zeebe.partition.id")))
        .isEqualTo(1L);
    assertThat(brokerClient.getAttributes().get(AttributeKey.stringKey("zeebe.broker.id")))
        .isNotBlank();
    assertThat(brokerClient.getAttributes().get(AttributeKey.stringKey("zeebe.value_type")))
        .isEqualTo("PROCESS_INSTANCE_CREATION");
    assertThat(brokerClient.getAttributes().get(AttributeKey.stringKey("zeebe.intent")))
        .isEqualTo("CREATE");
  }

  @Test
  void shouldConnectRestRequestToBrokerClientSpan() {
    // when
    try (final var client =
        BROKER
            .newClientBuilder()
            .preferRestOverGrpc(true)
            .withChainHandlers(new TraceparentChainHandler())
            .build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    // then
    final var chain = awaitChainFromBrokerClientSpan();
    assertThat(chain.getFirst().getName()).isEqualTo(BROKER_CLIENT_SPAN_NAME);
    assertThat(chain.getFirst().getKind()).isEqualTo(SpanKind.CLIENT);
    assertThat(chain.getLast().getKind()).isEqualTo(SpanKind.SERVER);
    assertThat(chain.getLast().getName()).startsWith("http post");
    assertChainIsConnected(chain);
  }

  @Test
  void shouldNotAddMetersForTracedGrpcRequests() {
    // given
    final var meterRegistry = BROKER.bean(MeterRegistry.class);

    // when
    try (final var client =
        BROKER.newClientBuilder().withInterceptors(new TraceparentClientInterceptor()).build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }
    awaitChainFromBrokerClientSpan();

    // then
    final var grpcMeterNames =
        meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .filter(name -> name.startsWith("grpc.server"))
            .collect(Collectors.toSet());
    assertThat(grpcMeterNames)
        .as("the observation of the gRPC call adds no meters to those of the gateway")
        .containsOnly(
            "grpc.server.requests.received",
            "grpc.server.responses.sent",
            "grpc.server.processing.duration");
  }

  @Test
  void shouldNotCreateSpansForUntracedRequests() {
    // when
    try (final var client = BROKER.newClientBuilder().build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    // then
    assertThat(SPANS.getFinishedSpanItems()).isEmpty();
  }

  @Test
  void shouldNotCreateSpanExporterWithoutEndpoint() {
    assertThatThrownBy(() -> BROKER.bean(SpanExporter.class))
        .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test
  void shouldAttachTraceExemplarToPrometheusMetricsForTracedRestRequest() throws Exception {
    // when
    try (final var client =
        BROKER
            .newClientBuilder()
            .preferRestOverGrpc(true)
            .withChainHandlers(new TraceparentChainHandler())
            .build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }
    awaitChainFromBrokerClientSpan();

    // then - non-lazy OTel SpanContext replaced Spring Boot's lazy bean and OpenMetrics scrape
    // includes a trace exemplar linking back to the sampled trace
    assertThat((Object) BROKER.bean("openTelemetryPrometheusSpanContext")).isNotNull();
    assertThatThrownBy(() -> BROKER.bean("spanContext"))
        .isInstanceOf(NoSuchBeanDefinitionException.class);

    final var request =
        java.net.http.HttpRequest.newBuilder(BROKER.actuatorUri("prometheus"))
            .header("Accept", "application/openmetrics-text; version=1.0.0; charset=utf-8")
            .GET()
            .build();
    final var response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("trace_id=\"" + TRACE_ID + "\"");
  }

  private static boolean isBrokerClientSpan(final SpanData span) {
    return BROKER_CLIENT_SPAN_NAME.equals(span.getName()) && span.getKind() == SpanKind.CLIENT;
  }

  /**
   * Returns the spans from the broker client span up to the gateway server span whose parent is the
   * client's span: broker client first, gateway last.
   */
  private static List<SpanData> awaitChainFromBrokerClientSpan() {
    final List<SpanData> chain = new ArrayList<>();
    Awaitility.await("the trace chain is exported")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              chain.clear();
              final var finished = SPANS.getFinishedSpanItems();
              var current =
                  finished.stream()
                      .filter(DistributedTracingIT::isBrokerClientSpan)
                      .findFirst()
                      .orElseThrow(
                          () -> new AssertionError("no broker client span in " + finished));
              chain.add(current);
              while (!CLIENT_SPAN_ID.equals(current.getParentSpanId())) {
                final var parentId = current.getParentSpanId();
                current =
                    finished.stream()
                        .filter(span -> span.getSpanId().equals(parentId))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new AssertionError(
                                    "span %s has an unknown parent %s in %s"
                                        .formatted(chain.getLast().getName(), parentId, finished)));
                chain.add(current);
              }
            });
    return chain;
  }

  private static void assertChainIsConnected(final List<SpanData> chain) {
    assertThat(chain).allMatch(span -> TRACE_ID.equals(span.getTraceId()));
    assertThat(chain.getLast().getParentSpanId()).isEqualTo(CLIENT_SPAN_ID);
  }

  private static final class TraceparentClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        final MethodDescriptor<ReqT, RespT> method,
        final CallOptions callOptions,
        final Channel next) {
      return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
        @Override
        public void start(final Listener<RespT> responseListener, final Metadata headers) {
          headers.put(
              Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER), TRACEPARENT);
          super.start(responseListener, headers);
        }
      };
    }
  }

  private static final class TraceparentChainHandler implements AsyncExecChainHandler {
    @Override
    public void execute(
        final HttpRequest request,
        final AsyncEntityProducer entityProducer,
        final AsyncExecChain.Scope scope,
        final AsyncExecChain chain,
        final AsyncExecCallback callback)
        throws HttpException, IOException {
      request.setHeader("traceparent", TRACEPARENT);
      chain.proceed(request, entityProducer, scope, callback);
    }
  }
}
