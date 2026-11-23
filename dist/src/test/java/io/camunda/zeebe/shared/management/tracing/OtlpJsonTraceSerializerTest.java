/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class OtlpJsonTraceSerializerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldSerializeStandardOtlpJsonAndScrubSensitiveAttributesAndStatusMessage()
      throws Exception {
    // given
    final var spans = new ArrayList<SpanData>();
    final var resource =
        Resource.create(
            Attributes.of(AttributeKey.stringKey("service.name"), "camunda"),
            "https://opentelemetry.io/schemas/1.26.0");

    try (final var provider =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(collectingProcessor(spans))
            .build()) {
      final var tracer =
          provider
              .tracerBuilder("io.camunda.test")
              .setInstrumentationVersion("1.0.0")
              .setSchemaUrl("https://opentelemetry.io/schemas/1.26.0")
              .build();
      final var linkedContext =
          SpanContext.createFromRemoteParent(
              "1af7651916cd43dd8448eb211c80319c",
              "a7ad6b7169203331",
              TraceFlags.getSampled(),
              TraceState.builder().put("vendor", "v1").build());
      final var parent =
          tracer
              .spanBuilder("zeebe.stream_processor PROCESS_INSTANCE_CREATION CREATE")
              .setSpanKind(SpanKind.SERVER)
              .addLink(
                  linkedContext,
                  Attributes.of(
                      AttributeKey.stringKey("link.kind"),
                      "batch",
                      AttributeKey.stringKey("url.query"),
                      "secret"))
              .setAttribute("zeebe.partition.id", 1L)
              .setAttribute("zeebe.value_type", "PROCESS_INSTANCE_CREATION")
              .setAttribute("flag", true)
              .setAttribute("ratio", Double.NaN)
              .setAttribute("http.route", "/v2/process-instances/{key}")
              .setAttribute("url.path", "/v2/users/alice-secret")
              .setAttribute("http.target", "/v2/users/alice?token=secret")
              .setAttribute("url.query", "secretToken=123")
              .setAttribute("url.fragment", "frag")
              .setAttribute("HTTP.REQUEST.HEADER.Authorization", "Bearer secret")
              .setAttribute("rpc.grpc.request.metadata.authorization", "Bearer grpc-secret")
              .setAttribute("db.statement", "SELECT * FROM secrets")
              .setAttribute("db.query.parameter.0", "secret-param")
              .setAttribute("user.email", "alice@customer.example")
              .setAttribute("enduser.id", "alice")
              .setAttribute(AttributeKey.stringArrayKey("tags"), Arrays.asList("a", null, "b"))
              .setAttribute(AttributeKey.longArrayKey("nums"), Arrays.asList(1L, null, 2L))
              .setAttribute(AttributeKey.booleanArrayKey("bools"), Arrays.asList(true, null))
              .setAttribute(
                  AttributeKey.doubleArrayKey("doubles"),
                  Arrays.asList(1.5, null, Double.POSITIVE_INFINITY))
              .startSpan();
      parent.recordException(new IllegalStateException("customerVariable=secretPayload"));
      parent.addEvent(
          "zeebe.retry",
          Attributes.of(
              AttributeKey.stringKey("zeebe.retry.reason"),
              "RESOURCE_EXHAUSTED",
              AttributeKey.stringKey("http.url"),
              "https://secret/path?token=xyz"));
      parent.setStatus(StatusCode.ERROR, "contains customer processId secret-123");
      parent.end();
    }

    // when
    final var serialized =
        OtlpJsonTraceSerializer.serializeResourceSpansArray(spans, "broker-0", 1024 * 1024);
    final var merged = wrapAsOtlpDocument(List.of(serialized.resourceSpansArrayJson()));
    final var root = MAPPER.readTree(merged);

    // then
    assertThat(serialized.exportedSpans()).isEqualTo(1);
    assertThat(serialized.truncated()).isFalse();

    final var resourceSpan = root.path("resourceSpans").get(0);
    assertThat(resourceSpan.path("schemaUrl").asText())
        .isEqualTo("https://opentelemetry.io/schemas/1.26.0");
    final var resourceAttrs = attributesAsMap(resourceSpan.path("resource").path("attributes"));
    assertThat(resourceAttrs).containsEntry("service.name", "camunda");
    assertThat(resourceAttrs).containsEntry("service.instance.id", "broker-0");

    final var scopeSpan = resourceSpan.path("scopeSpans").get(0);
    assertThat(scopeSpan.path("schemaUrl").asText())
        .isEqualTo("https://opentelemetry.io/schemas/1.26.0");
    assertThat(scopeSpan.path("scope").path("name").asText()).isEqualTo("io.camunda.test");
    assertThat(scopeSpan.path("scope").path("version").asText()).isEqualTo("1.0.0");

    final var spanJson = scopeSpan.path("spans").get(0);
    assertThat(spanJson.path("traceId").asText()).hasSize(32);
    assertThat(spanJson.path("spanId").asText()).hasSize(16);
    assertThat(spanJson.path("name").asText())
        .isEqualTo("zeebe.stream_processor PROCESS_INSTANCE_CREATION CREATE");
    assertThat(spanJson.path("kind").asInt()).isEqualTo(2); // SERVER
    assertThat(spanJson.path("startTimeUnixNano").isTextual()).isTrue();
    assertThat(spanJson.path("endTimeUnixNano").isTextual()).isTrue();
    assertThat(spanJson.path("status").path("code").asInt()).isEqualTo(2); // ERROR
    assertThat(spanJson.path("status").has("message")).isFalse();

    final var spanAttrs = attributesAsMap(spanJson.path("attributes"));
    assertThat(spanAttrs)
        .containsEntry("zeebe.partition.id", "1")
        .containsEntry("zeebe.value_type", "PROCESS_INSTANCE_CREATION")
        .containsEntry("http.route", "/v2/process-instances/{key}")
        .doesNotContainKeys(
            "url.path",
            "http.target",
            "url.query",
            "url.fragment",
            "HTTP.REQUEST.HEADER.Authorization",
            "rpc.grpc.request.metadata.authorization",
            "db.statement",
            "db.query.parameter.0",
            "user.email",
            "enduser.id");

    // Verify null array elements serialize as empty {} AnyValue objects without NPE, and non-finite
    // doubles serialize as strings per proto3 JSON mapping
    final var tagsAttr = findAttribute(spanJson.path("attributes"), "tags");
    assertThat(tagsAttr.path("value").path("arrayValue").path("values").get(1).isEmpty()).isTrue();
    final var ratioAttr = findAttribute(spanJson.path("attributes"), "ratio");
    assertThat(ratioAttr.path("value").path("doubleValue").asText()).isEqualTo("NaN");
    final var linkJson = spanJson.path("links").get(0);
    assertThat(linkJson.path("traceId").asText()).isEqualTo("1af7651916cd43dd8448eb211c80319c");
    assertThat(linkJson.path("traceState").asText()).isEqualTo("vendor=v1");
    assertThat(attributesAsMap(linkJson.path("attributes")))
        .containsEntry("link.kind", "batch")
        .doesNotContainKey("url.query");

    // Exception event retains exception.type but scrubs exception.message and exception.stacktrace
    final var exceptionEvent = spanJson.path("events").get(0);
    assertThat(exceptionEvent.path("name").asText()).isEqualTo("exception");
    final var exceptionAttrs = attributesAsMap(exceptionEvent.path("attributes"));
    assertThat(exceptionAttrs)
        .containsEntry("exception.type", "java.lang.IllegalStateException")
        .doesNotContainKeys("exception.message", "exception.stacktrace");

    final var retryEvent = spanJson.path("events").get(1);
    assertThat(retryEvent.path("name").asText()).isEqualTo("zeebe.retry");
    final var eventAttrs = attributesAsMap(retryEvent.path("attributes"));
    assertThat(eventAttrs)
        .containsEntry("zeebe.retry.reason", "RESOURCE_EXHAUSTED")
        .doesNotContainKey("http.url");
  }

  @Test
  void shouldStrictlyEnforceMaxBytesIncludingLargeResourceHeaders() throws Exception {
    // given - resource with ~1.5 KB of attributes + 30 spans
    final var spans = new ArrayList<SpanData>();
    final var largeResource =
        Resource.create(
            Attributes.of(
                AttributeKey.stringKey("service.name"),
                "camunda",
                AttributeKey.stringKey("k8s.pod.annotations"),
                "a".repeat(1500)),
            "https://opentelemetry.io/schemas/1.26.0");

    try (final var provider =
        SdkTracerProvider.builder()
            .setResource(largeResource)
            .addSpanProcessor(collectingProcessor(spans))
            .build()) {
      final var tracer = provider.get("test");
      for (int i = 0; i < 30; i++) {
        tracer.spanBuilder("span-" + i).setAttribute("payload", "x".repeat(120)).startSpan().end();
      }
    }

    // when - tight byte cap forces truncation while accounting for resource/scope headers
    final int maxBytes = 2_500;
    final var serialized =
        OtlpJsonTraceSerializer.serializeResourceSpansArray(spans, "0", maxBytes);
    final var parsedArray = MAPPER.readTree(serialized.resourceSpansArrayJson());

    // then - valid JSON and strictly <= maxBytes
    assertThat(serialized.truncated()).isTrue();
    assertThat(serialized.exportedSpans()).isBetween(1, 29);
    assertThat(serialized.resourceSpansArrayJson().length).isLessThanOrEqualTo(maxBytes);
    assertThat(parsedArray.get(0).path("scopeSpans").get(0).path("spans").size())
        .isEqualTo(serialized.exportedSpans());
  }

  @Test
  void shouldStreamMultipleNodesAndScopesAndPreserveExistingServiceInstanceId() throws Exception {
    // given - nodeA already has explicit service.instance.id and two distinct scopes
    final var spansA = new ArrayList<SpanData>();
    final var spansB = new ArrayList<SpanData>();
    final var resourceWithInstanceId =
        Resource.create(
            Attributes.of(
                AttributeKey.stringKey("service.name"),
                "camunda",
                AttributeKey.stringKey("service.instance.id"),
                "custom-instance-id"));

    try (final var providerA =
            SdkTracerProvider.builder()
                .setResource(resourceWithInstanceId)
                .addSpanProcessor(collectingProcessor(spansA))
                .build();
        final var providerB =
            SdkTracerProvider.builder().addSpanProcessor(collectingProcessor(spansB)).build()) {
      providerA.get("scope-1").spanBuilder("gw-span-1").startSpan().end();
      providerA.get("scope-2").spanBuilder("gw-span-2").startSpan().end();
      providerB.get("broker").spanBuilder("broker-span").startSpan().end();
    }

    final var nodeA = OtlpJsonTraceSerializer.serializeResourceSpansArray(spansA, "gateway", 65536);
    final var nodeB = OtlpJsonTraceSerializer.serializeResourceSpansArray(spansB, "0", 65536);
    final var emptyNode =
        OtlpJsonTraceSerializer.serializeResourceSpansArray(List.of(), "1", 65536);

    // when
    final var merged =
        wrapAsOtlpDocument(
            List.of(
                nodeA.resourceSpansArrayJson(),
                emptyNode.resourceSpansArrayJson(),
                nodeB.resourceSpansArrayJson()));

    // then
    final var otlpRoot = MAPPER.readTree(merged);
    assertThat(otlpRoot.path("resourceSpans").size()).isEqualTo(2);
    final var nodeAAttrs =
        attributesAsMap(otlpRoot.path("resourceSpans").get(0).path("resource").path("attributes"));
    assertThat(nodeAAttrs).containsEntry("service.instance.id", "custom-instance-id");
    assertThat(otlpRoot.path("resourceSpans").get(0).path("scopeSpans").size()).isEqualTo(2);
  }

  private static byte[] wrapAsOtlpDocument(final List<byte[]> nodeArrays) throws Exception {
    final var out = new ByteArrayOutputStream();
    out.write(OtlpJsonTraceSerializer.OTLP_PREFIX);
    boolean hasElements = false;
    for (final var array : nodeArrays) {
      hasElements = OtlpJsonTraceSerializer.writeResourceSpansElements(out, array, hasElements);
    }
    out.write(OtlpJsonTraceSerializer.OTLP_SUFFIX);
    return out.toByteArray();
  }

  private static SpanProcessor collectingProcessor(final List<SpanData> target) {
    return new SpanProcessor() {
      @Override
      public void onStart(final Context parentContext, final ReadWriteSpan span) {}

      @Override
      public boolean isStartRequired() {
        return false;
      }

      @Override
      public void onEnd(final ReadableSpan span) {
        target.add(span.toSpanData());
      }

      @Override
      public boolean isEndRequired() {
        return true;
      }
    };
  }

  private static JsonNode findAttribute(final JsonNode attributesNode, final String key) {
    for (final var attr : attributesNode) {
      if (key.equals(attr.path("key").asText())) {
        return attr;
      }
    }
    throw new AssertionError("Attribute not found: " + key);
  }

  private static Map<String, String> attributesAsMap(final JsonNode attributesNode) {
    final var map = new HashMap<String, String>();
    for (final var attr : attributesNode) {
      final var key = attr.path("key").asText();
      final var val = attr.path("value");
      if (val.has("stringValue")) {
        map.put(key, val.path("stringValue").asText());
      } else if (val.has("intValue")) {
        map.put(key, val.path("intValue").asText());
      } else if (val.has("boolValue")) {
        map.put(key, val.path("boolValue").asText());
      } else if (val.has("doubleValue")) {
        map.put(key, val.path("doubleValue").asText());
      }
    }
    return map;
  }
}
