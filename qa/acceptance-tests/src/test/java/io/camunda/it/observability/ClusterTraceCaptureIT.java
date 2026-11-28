/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies cluster-wide on-demand trace capture and ZIP export over {@code /actuator/traces} across
 * a standalone gateway and multiple brokers with no external OTel collector configured.
 */
@ZeebeIntegration
final class ClusterTraceCaptureIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String BROKER_CLIENT_SPAN_NAME =
      "zeebe.broker_client PROCESS_INSTANCE_CREATION CREATE";
  private static final String STREAM_PROCESSOR_SPAN_NAME =
      "zeebe.stream_processor PROCESS_INSTANCE_CREATION CREATE";

  @TestZeebe(purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .build();

  private static String processId;

  @BeforeAll
  static void deployProcess() {
    processId = "cluster-trace-capture-" + UUID.randomUUID();
    try (final var client = CLUSTER.newClientBuilder().build()) {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("task", t -> t.zeebeJobType("trace-capture-task"))
                  .endEvent()
                  .done(),
              "process.bpmn")
          .send()
          .join();
    }
  }

  @AfterEach
  void clearClusterTraces() throws Exception {
    final var response = delete(CLUSTER.anyGateway().actuatorUri("traces"));
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void shouldCaptureAndExportConnectedTraceAcrossGatewayAndBrokerZipBundle() throws Exception {
    // given - start cluster-wide active sampling from the standalone gateway
    final var gateway = CLUSTER.anyGateway();
    final var startResponse =
        postJson(
            gateway.actuatorUri("traces"),
            """
            {"duration":"60s","samplingProbability":1.0,"maxTracesPerSecond":50,"maxSpansPerNode":5000}
            """);
    assertThat(startResponse.statusCode()).isEqualTo(200);
    final var startJson = MAPPER.readTree(startResponse.body());
    assertThat(startJson.path("active").asBoolean()).isTrue();
    assertThat(startJson.path("sdkEnabled").asBoolean()).isTrue();
    assertThat(startJson.path("expectedNodes").asInt()).isEqualTo(3);
    assertThat(startJson.path("respondedNodes").asInt()).isEqualTo(3);
    assertThat(startJson.path("errors").isEmpty()).isTrue();
    assertThat(startJson.path("nodes").size()).isEqualTo(3);

    // starting another capture while active returns 409 Conflict
    assertThat(postJson(gateway.actuatorUri("traces"), "{}").statusCode()).isEqualTo(409);

    // when - send a normal untraced gRPC request (no traceparent header)
    try (final var client = CLUSTER.newClientBuilder().build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    // then - download the cluster-wide ZIP from the gateway once broker spans have finished
    final var zipEntries = new HashMap<String, byte[]>();
    Awaitility.await("complete gateway + broker trace appears in cluster export")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var exportResponse = getBytes(gateway.actuatorUri("traces", "export"));
              assertThat(exportResponse.statusCode()).isEqualTo(200);
              assertThat(exportResponse.headers().firstValue("Content-Type"))
                  .hasValueSatisfying(ct -> assertThat(ct).contains("application/zip"));

              zipEntries.clear();
              zipEntries.putAll(unzip(exportResponse.body()));
              assertThat(zipEntries).containsOnlyKeys("traces.otlp.json", "metadata.json");

              final var otlp = MAPPER.readTree(zipEntries.get("traces.otlp.json"));
              final var spans = flattenSpans(otlp);

              final var brokerServerSpan =
                  spans.stream()
                      .filter(s -> STREAM_PROCESSOR_SPAN_NAME.equals(s.name()) && s.kind() == 2)
                      .findFirst()
                      .orElseThrow(
                          () -> new AssertionError("missing broker SERVER span in " + spans));

              final var traceSpans =
                  spans.stream()
                      .filter(s -> brokerServerSpan.traceId().equals(s.traceId()))
                      .toList();

              // Broker stages + broker SERVER + gateway broker-client CLIENT + gRPC gateway SERVER
              assertThat(traceSpans)
                  .extracting(ExportedSpan::name)
                  .contains(
                      "gateway_protocol.Gateway/CreateProcessInstance",
                      BROKER_CLIENT_SPAN_NAME,
                      STREAM_PROCESSOR_SPAN_NAME,
                      "zeebe.process",
                      "zeebe.write",
                      "zeebe.update_state",
                      "zeebe.await_commit");

              // Spans came from two distinct cluster members: gateway + partition leader broker
              final var instances =
                  traceSpans.stream().map(ExportedSpan::instanceId).distinct().toList();
              assertThat(instances).hasSize(2);
              assertThat(instances).contains(gateway.nodeId().id());

              // Parent chain connects broker SERVER -> gateway CLIENT -> gRPC SERVER
              final var byId = new HashMap<String, ExportedSpan>();
              traceSpans.forEach(s -> byId.put(s.spanId(), s));
              final var brokerClientSpan = byId.get(brokerServerSpan.parentSpanId());
              assertThat(brokerClientSpan).isNotNull();
              assertThat(brokerClientSpan.kind()).isEqualTo(3); // CLIENT
              assertThat(brokerClientSpan.instanceId()).isEqualTo(gateway.nodeId().id());

              final var grpcServerSpan = byId.get(brokerClientSpan.parentSpanId());
              assertThat(grpcServerSpan).isNotNull();
              assertThat(grpcServerSpan.name())
                  .isEqualTo("gateway_protocol.Gateway/CreateProcessInstance");
              assertThat(grpcServerSpan.instanceId()).isEqualTo(gateway.nodeId().id());

              // No /actuator/traces self-spans polluted the export
              assertThat(spans).noneMatch(s -> s.name().contains("/actuator/traces"));
            });

    final var metadata = MAPPER.readTree(zipEntries.get("metadata.json"));
    assertThat(metadata.path("camundaVersion").asText()).isNotBlank();
    assertThat(metadata.path("expectedNodes").asInt()).isEqualTo(3);
    assertThat(metadata.path("respondedNodes").asInt()).isEqualTo(3);
    assertThat(metadata.path("errors").isEmpty()).isTrue();
    assertThat(metadata.path("truncated").asBoolean()).isFalse();
    assertThat(metadata.path("nodes").size()).isEqualTo(3);
    assertThat(metadata.path("totalExportedSpans").asInt()).isPositive();
  }

  @Test
  void shouldCaptureHeaderTracedRequestAndExportFilteredZipFromBroker() throws Exception {
    // given - POST without JSON Content-Type fails loudly with 415 instead of ignoring the body
    final var gateway = CLUSTER.anyGateway();
    final var broker = CLUSTER.brokers().values().iterator().next();
    final var missingContentType =
        HTTP.send(
            HttpRequest.newBuilder(gateway.actuatorUri("traces"))
                .POST(BodyPublishers.ofString("{\"samplingProbability\":1.0}"))
                .build(),
            BodyHandlers.ofByteArray());
    assertThat(missingContentType.statusCode()).isEqualTo(415);

    // invalid traceId returns 400 JSON
    final var invalidTraceId =
        getBytes(URI.create(broker.actuatorUri("traces", "export") + "?traceId=not-hex"));
    assertThat(invalidTraceId.statusCode()).isEqualTo(400);

    // start header-only capture (default samplingProbability = 0.0)
    final var startResponse = postJson(gateway.actuatorUri("traces"), "{}");
    assertThat(startResponse.statusCode()).isEqualTo(200);

    final var traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    final var clientSpanId = "00f067aa0ba902b7";
    final var traceparent = "00-" + traceId + "-" + clientSpanId + "-01";

    // when - send one untraced request and one REST request carrying traceparent
    try (final var untracedClient = CLUSTER.newClientBuilder().preferRestOverGrpc(true).build();
        final var tracedClient =
            CLUSTER
                .newClientBuilder()
                .preferRestOverGrpc(true)
                .withChainHandlers(new TraceparentChainHandler(traceparent))
                .build()) {
      untracedClient
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .send()
          .join();
      tracedClient
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .send()
          .join();
    }

    // then - query /actuator/traces/export?traceId=... on a BROKER (proving any node coordinates
    // cluster-wide export including standalone gateway spans)
    final var filteredUri =
        URI.create(broker.actuatorUri("traces", "export") + "?traceId=" + traceId);
    Awaitility.await("filtered ZIP contains gateway REST + broker spans")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var response = getBytes(filteredUri);
              assertThat(response.statusCode()).isEqualTo(200);
              final var entries = unzip(response.body());
              final var spans = flattenSpans(MAPPER.readTree(entries.get("traces.otlp.json")));
              assertThat(spans).isNotEmpty().allMatch(s -> traceId.equals(s.traceId()));
              assertThat(spans)
                  .extracting(ExportedSpan::name)
                  .contains(
                      BROKER_CLIENT_SPAN_NAME,
                      STREAM_PROCESSOR_SPAN_NAME,
                      "zeebe.process",
                      "zeebe.write",
                      "zeebe.update_state",
                      "zeebe.await_commit");
              assertThat(spans).anyMatch(s -> s.name().startsWith("http post"));
              assertThat(spans)
                  .extracting(ExportedSpan::instanceId)
                  .contains(gateway.nodeId().id());
              // Verify sensitive HTTP path and status.message fields were scrubbed
              assertThat(spans).noneMatch(s -> s.attributeKeys().contains("url.path"));
              assertThat(spans).noneMatch(ExportedSpan::hasStatusMessage);
            });

    // and - unfiltered capture contains ONLY the header-traced trace (untraced request was skipped)
    final var unfilteredResponse = getBytes(broker.actuatorUri("traces", "export"));
    assertThat(unfilteredResponse.statusCode()).isEqualTo(200);
    final var unfilteredEntries = unzip(unfilteredResponse.body());
    final var unfilteredSpans =
        flattenSpans(MAPPER.readTree(unfilteredEntries.get("traces.otlp.json")));
    assertThat(unfilteredSpans).isNotEmpty().allMatch(s -> traceId.equals(s.traceId()));
  }

  @Test
  void shouldStopAndClearClusterBuffersOnDelete() throws Exception {
    // given
    final var gateway = CLUSTER.anyGateway();
    assertThat(
            postJson(
                    gateway.actuatorUri("traces"),
                    """
                    {"duration":"60s","samplingProbability":1.0,"maxTracesPerSecond":50}
                    """)
                .statusCode())
        .isEqualTo(200);

    try (final var client = CLUSTER.newClientBuilder().build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }

    Awaitility.await("spans captured")
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var status = MAPPER.readTree(getBytes(gateway.actuatorUri("traces")).body());
              assertThat(status.path("totalCapturedSpans").asInt()).isPositive();
            });

    // when - delete stops capture and clears all cluster buffers
    final var clearResponse = delete(gateway.actuatorUri("traces"));
    assertThat(clearResponse.statusCode()).isEqualTo(200);
    final var clearedStatus = MAPPER.readTree(clearResponse.body());
    assertThat(clearedStatus.path("active").asBoolean()).isFalse();
    assertThat(clearedStatus.path("totalCapturedSpans").asInt()).isZero();

    // additional untraced traffic after delete is not captured
    try (final var client = CLUSTER.newClientBuilder().build()) {
      client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
    }
    final var afterClearStatus = MAPPER.readTree(getBytes(gateway.actuatorUri("traces")).body());
    assertThat(afterClearStatus.path("totalCapturedSpans").asInt()).isZero();
  }

  private static HttpResponse<byte[]> postJson(final URI uri, final String json) throws Exception {
    final var request =
        HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();
    return HTTP.send(request, BodyHandlers.ofByteArray());
  }

  private static HttpResponse<byte[]> getBytes(final URI uri) throws Exception {
    final var request = HttpRequest.newBuilder(uri).GET().build();
    return HTTP.send(request, BodyHandlers.ofByteArray());
  }

  private static HttpResponse<byte[]> delete(final URI uri) throws Exception {
    final var request = HttpRequest.newBuilder(uri).DELETE().build();
    return HTTP.send(request, BodyHandlers.ofByteArray());
  }

  private static Map<String, byte[]> unzip(final byte[] zipBytes) throws IOException {
    final var entries = new HashMap<String, byte[]>();
    try (final var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        entries.put(entry.getName(), zis.readAllBytes());
      }
    }
    return entries;
  }

  private static List<ExportedSpan> flattenSpans(final JsonNode otlpRoot) {
    final var result = new ArrayList<ExportedSpan>();
    for (final var resourceSpan : otlpRoot.path("resourceSpans")) {
      String instanceId = "";
      for (final var attr : resourceSpan.path("resource").path("attributes")) {
        if ("service.instance.id".equals(attr.path("key").asText())) {
          instanceId = attr.path("value").path("stringValue").asText();
        }
      }
      for (final var scopeSpan : resourceSpan.path("scopeSpans")) {
        for (final var span : scopeSpan.path("spans")) {
          result.add(
              new ExportedSpan(
                  instanceId,
                  span.path("traceId").asText(),
                  span.path("spanId").asText(),
                  span.path("parentSpanId").asText(),
                  span.path("name").asText(),
                  span.path("kind").asInt(),
                  extractAttributeKeys(span),
                  span.path("status").has("message")));
        }
      }
    }
    return result;
  }

  private static List<String> extractAttributeKeys(final JsonNode span) {
    final var keys = new ArrayList<String>();
    for (final var attr : span.path("attributes")) {
      keys.add(attr.path("key").asText());
    }
    return keys;
  }

  private record ExportedSpan(
      String instanceId,
      String traceId,
      String spanId,
      String parentSpanId,
      String name,
      int kind,
      List<String> attributeKeys,
      boolean hasStatusMessage) {}

  private record TraceparentChainHandler(String traceparent) implements AsyncExecChainHandler {
    @Override
    public void execute(
        final org.apache.hc.core5.http.HttpRequest request,
        final AsyncEntityProducer entityProducer,
        final AsyncExecChain.Scope scope,
        final AsyncExecChain chain,
        final AsyncExecCallback asyncExecCallback)
        throws HttpException, IOException {
      request.setHeader("traceparent", traceparent);
      chain.proceed(request, entityProducer, scope, asyncExecCallback);
    }
  }
}
