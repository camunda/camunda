/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management.tracing;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Serializes {@link SpanData} to standard OTLP/HTTP JSON ({@code ExportTraceServiceRequest}) and
 * streams per-node {@code resourceSpans} arrays straight into a support ZIP bundle without
 * intermediate re-parsing or full-cluster copies.
 *
 * <p>Sensitive attributes (URLs/paths/queries, HTTP/gRPC headers, SQL statements, exception
 * messages/stacktraces, user identifiers) and free-form {@code status.message} strings are stripped
 * during serialization so exported bundles remain safe to attach to support tickets.
 */
@NullMarked
public final class OtlpJsonTraceSerializer {

  static final byte[] OTLP_PREFIX = "{\"resourceSpans\":[".getBytes(StandardCharsets.UTF_8);
  static final byte[] OTLP_SUFFIX = "]}".getBytes(StandardCharsets.UTF_8);

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final AttributeKey<String> SERVICE_INSTANCE_ID =
      AttributeKey.stringKey("service.instance.id");
  private static final byte[] EMPTY_ARRAY = "[]".getBytes(StandardCharsets.UTF_8);

  /** Safety margin for outer brackets and separators around a span entry. */
  private static final int CLOSING_HEADROOM_BYTES = 16;

  private static final Set<String> SCRUBBED_ATTRIBUTE_KEYS =
      Set.of(
          "url.query",
          "url.full",
          "url.path",
          "url.fragment",
          "http.url",
          "http.target",
          "db.statement",
          "db.query.text",
          "exception.message",
          "exception.stacktrace",
          "enduser.id",
          "enduser.role",
          "enduser.scope");
  private static final List<String> SCRUBBED_ATTRIBUTE_PREFIXES =
      List.of(
          "http.request.header.",
          "http.response.header.",
          "rpc.grpc.request.metadata.",
          "rpc.grpc.response.metadata.",
          "db.query.parameter.",
          "user.");

  private OtlpJsonTraceSerializer() {}

  /**
   * Serializes a node's spans as a raw UTF-8 JSON array of OTLP {@code ResourceSpans} objects,
   * accounting for resource and scope header bytes so the output never exceeds {@code maxBytes} and
   * is always syntactically valid JSON.
   */
  public static SerializedNodeSpans serializeResourceSpansArray(
      final List<SpanData> spans, final String memberId, final int maxBytes) {
    if (spans.isEmpty() || maxBytes < EMPTY_ARRAY.length) {
      return new SerializedNodeSpans(EMPTY_ARRAY, 0, !spans.isEmpty());
    }

    final Map<Resource, Map<InstrumentationScopeInfo, List<SpanData>>> grouped =
        new LinkedHashMap<>();
    for (final var span : spans) {
      grouped
          .computeIfAbsent(span.getResource(), ignored -> new LinkedHashMap<>())
          .computeIfAbsent(span.getInstrumentationScopeInfo(), ignored -> new ArrayList<>())
          .add(span);
    }

    final var out = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
    final var resourceHeaderBuf = new ByteArrayOutputStream(512);
    final var scopeHeaderBuf = new ByteArrayOutputStream(256);
    final var spanScratch = new ByteArrayOutputStream(1024);
    int exported = 0;
    boolean truncated = false;
    boolean firstResource = true;

    try {
      out.write('[');

      outer:
      for (final var resourceEntry : grouped.entrySet()) {
        final var resource = resourceEntry.getKey();
        final var scopes = resourceEntry.getValue();
        final byte[] resourceHeader =
            serializeResourceHeader(resourceHeaderBuf, resource, memberId);
        final byte[] resourceFooter = serializeFooter(resource.getSchemaUrl());
        boolean resourceOpened = false;
        boolean firstScope = true;

        for (final var scopeEntry : scopes.entrySet()) {
          final var scope = scopeEntry.getKey();
          final var scopeSpans = scopeEntry.getValue();
          final byte[] scopeHeader = serializeScopeHeader(scopeHeaderBuf, scope);
          final byte[] scopeFooter = serializeFooter(scope.getSchemaUrl());
          boolean scopeOpened = false;
          boolean firstSpan = true;

          for (final var span : scopeSpans) {
            spanScratch.reset();
            try (final var spanGen = JSON_FACTORY.createGenerator(spanScratch, JsonEncoding.UTF8)) {
              writeSpan(spanGen, span);
            }

            final int needed =
                (!resourceOpened ? resourceHeader.length + (firstResource ? 0 : 1) : 0)
                    + (!scopeOpened ? scopeHeader.length + (firstScope ? 0 : 1) : 0)
                    + spanScratch.size()
                    + (firstSpan ? 0 : 1)
                    + scopeFooter.length
                    + resourceFooter.length
                    + CLOSING_HEADROOM_BYTES;

            if (out.size() + needed > maxBytes) {
              truncated = true;
              if (scopeOpened) {
                out.write(scopeFooter);
              }
              if (resourceOpened) {
                out.write(resourceFooter);
              }
              break outer;
            }

            if (!resourceOpened) {
              if (!firstResource) {
                out.write(',');
              }
              out.write(resourceHeader);
              resourceOpened = true;
              firstResource = false;
            }
            if (!scopeOpened) {
              if (!firstScope) {
                out.write(',');
              }
              out.write(scopeHeader);
              scopeOpened = true;
              firstScope = false;
            }
            if (!firstSpan) {
              out.write(',');
            }
            spanScratch.writeTo(out);
            firstSpan = false;
            exported++;
          }

          if (scopeOpened) {
            out.write(scopeFooter);
          }
        }

        if (resourceOpened) {
          out.write(resourceFooter);
        }
      }

      out.write(']');
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to serialize spans to OTLP JSON", e);
    }

    return new SerializedNodeSpans(out.toByteArray(), exported, truncated);
  }

  /**
   * Validates that {@code arrayBytes} is framed as a JSON array ({@code [...]}). Call before
   * streaming peer payloads so malformed replies fail cleanly per-node rather than mid-stream.
   */
  static void validateResourceSpansArray(final byte[] arrayBytes) {
    innerArraySlice(arrayBytes);
  }

  /**
   * Writes the inner elements of a serialized {@code ResourceSpans} JSON array directly to {@code
   * out}, inserting a leading comma when {@code alreadyHasElements} is {@code true}. Returns
   * whether at least one element has been written so far.
   */
  public static boolean writeResourceSpansElements(
      final OutputStream out, final byte[] arrayBytes, final boolean alreadyHasElements)
      throws IOException {
    final var slice = innerArraySlice(arrayBytes);
    if (slice.length == 0) {
      return alreadyHasElements;
    }
    if (alreadyHasElements) {
      out.write(',');
    }
    out.write(arrayBytes, slice.offset, slice.length);
    return true;
  }

  private static byte[] serializeResourceHeader(
      final ByteArrayOutputStream scratch, final Resource resource, final String memberId)
      throws IOException {
    scratch.reset();
    try (final var gen = JSON_FACTORY.createGenerator(scratch, JsonEncoding.UTF8)) {
      gen.writeStartObject();
      writeResource(gen, resource, memberId);
      gen.writeEndObject();
    }
    // Strip trailing '}' and append ',"scopeSpans":[' so spans stream directly into the array.
    final byte[] full = scratch.toByteArray();
    final byte[] tail = ",\"scopeSpans\":[".getBytes(StandardCharsets.UTF_8);
    final byte[] header = new byte[full.length - 1 + tail.length];
    System.arraycopy(full, 0, header, 0, full.length - 1);
    System.arraycopy(tail, 0, header, full.length - 1, tail.length);
    return header;
  }

  private static byte[] serializeFooter(final @Nullable String schemaUrl) throws IOException {
    if (schemaUrl == null || schemaUrl.isEmpty()) {
      return "]}".getBytes(StandardCharsets.UTF_8);
    }
    final var out = new ByteArrayOutputStream(schemaUrl.length() + 24);
    out.write(']');
    out.write(',');
    writeJsonField(out, "schemaUrl", schemaUrl);
    out.write('}');
    return out.toByteArray();
  }

  private static byte[] serializeScopeHeader(
      final ByteArrayOutputStream scratch, final InstrumentationScopeInfo scope)
      throws IOException {
    scratch.reset();
    try (final var gen = JSON_FACTORY.createGenerator(scratch, JsonEncoding.UTF8)) {
      gen.writeStartObject();
      writeScope(gen, scope);
      gen.writeEndObject();
    }
    final byte[] full = scratch.toByteArray();
    final byte[] tail = ",\"spans\":[".getBytes(StandardCharsets.UTF_8);
    final byte[] header = new byte[full.length - 1 + tail.length];
    System.arraycopy(full, 0, header, 0, full.length - 1);
    System.arraycopy(tail, 0, header, full.length - 1, tail.length);
    return header;
  }

  private static void writeJsonField(
      final ByteArrayOutputStream out, final String name, final String value) throws IOException {
    final var scratch = new ByteArrayOutputStream(name.length() + value.length() + 16);
    try (final var gen = JSON_FACTORY.createGenerator(scratch, JsonEncoding.UTF8)) {
      gen.writeStartObject();
      gen.writeStringField(name, value);
      gen.writeEndObject();
    }
    final byte[] obj = scratch.toByteArray();
    // Write inner `"name":"escapedValue"` without the enclosing `{` and `}`.
    out.write(obj, 1, obj.length - 2);
  }

  private static ArraySlice innerArraySlice(final byte[] arrayBytes) {
    int start = 0;
    int end = arrayBytes.length;
    while (start < end && isAsciiWhitespace(arrayBytes[start])) {
      start++;
    }
    while (end > start && isAsciiWhitespace(arrayBytes[end - 1])) {
      end--;
    }
    if (end - start < 2 || arrayBytes[start] != '[' || arrayBytes[end - 1] != ']') {
      throw new IllegalArgumentException("Expected JSON array of resourceSpans");
    }
    start++;
    end--;
    while (start < end && isAsciiWhitespace(arrayBytes[start])) {
      start++;
    }
    while (end > start && isAsciiWhitespace(arrayBytes[end - 1])) {
      end--;
    }
    return new ArraySlice(start, end - start);
  }

  private static boolean isAsciiWhitespace(final byte b) {
    return b == ' ' || b == '\n' || b == '\r' || b == '\t';
  }

  private static void writeResource(
      final JsonGenerator gen, final Resource resource, final String memberId) throws IOException {
    gen.writeObjectFieldStart("resource");
    gen.writeArrayFieldStart("attributes");
    writeAttributesEntries(gen, resource.getAttributes(), false);
    if (!memberId.isBlank() && resource.getAttributes().get(SERVICE_INSTANCE_ID) == null) {
      gen.writeStartObject();
      gen.writeStringField("key", SERVICE_INSTANCE_ID.getKey());
      gen.writeObjectFieldStart("value");
      gen.writeStringField("stringValue", memberId);
      gen.writeEndObject();
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static void writeScope(final JsonGenerator gen, final InstrumentationScopeInfo scope)
      throws IOException {
    gen.writeObjectFieldStart("scope");
    gen.writeStringField("name", scope.getName());
    if (scope.getVersion() != null && !scope.getVersion().isEmpty()) {
      gen.writeStringField("version", scope.getVersion());
    }
    if (!scope.getAttributes().isEmpty()) {
      gen.writeArrayFieldStart("attributes");
      writeAttributesEntries(gen, scope.getAttributes(), true);
      gen.writeEndArray();
    }
    gen.writeEndObject();
  }

  private static void writeSpan(final JsonGenerator gen, final SpanData span) throws IOException {
    final var ctx = span.getSpanContext();
    final var parent = span.getParentSpanContext();

    gen.writeStartObject();
    gen.writeStringField("traceId", ctx.getTraceId());
    gen.writeStringField("spanId", ctx.getSpanId());

    final var traceState = formatTraceState(ctx.getTraceState());
    if (!traceState.isEmpty()) {
      gen.writeStringField("traceState", traceState);
    }
    if (parent.isValid()) {
      gen.writeStringField("parentSpanId", parent.getSpanId());
    }
    gen.writeNumberField("flags", encodeSpanFlags(ctx.getTraceFlags().asByte(), parent.isRemote()));
    gen.writeStringField("name", span.getName());
    gen.writeNumberField("kind", toProtoSpanKind(span.getKind()));
    gen.writeStringField("startTimeUnixNano", Long.toUnsignedString(span.getStartEpochNanos()));
    gen.writeStringField("endTimeUnixNano", Long.toUnsignedString(span.getEndEpochNanos()));

    if (!span.getAttributes().isEmpty()) {
      gen.writeArrayFieldStart("attributes");
      writeAttributesEntries(gen, span.getAttributes(), true);
      gen.writeEndArray();
    }
    final int droppedAttributes = span.getTotalAttributeCount() - span.getAttributes().size();
    if (droppedAttributes > 0) {
      gen.writeNumberField("droppedAttributesCount", droppedAttributes);
    }

    if (!span.getEvents().isEmpty()) {
      gen.writeArrayFieldStart("events");
      for (final var event : span.getEvents()) {
        writeEvent(gen, event);
      }
      gen.writeEndArray();
    }
    final int droppedEvents = span.getTotalRecordedEvents() - span.getEvents().size();
    if (droppedEvents > 0) {
      gen.writeNumberField("droppedEventsCount", droppedEvents);
    }

    if (!span.getLinks().isEmpty()) {
      gen.writeArrayFieldStart("links");
      for (final var link : span.getLinks()) {
        writeLink(gen, link);
      }
      gen.writeEndArray();
    }
    final int droppedLinks = span.getTotalRecordedLinks() - span.getLinks().size();
    if (droppedLinks > 0) {
      gen.writeNumberField("droppedLinksCount", droppedLinks);
    }

    // Omit status.message because broker errors and framework exceptions can contain customer
    // identifiers or payloads; status.code, exception.type, zeebe.error.code, and
    // zeebe.rejection.type carry the structured diagnostic signal instead.
    gen.writeObjectFieldStart("status");
    gen.writeNumberField("code", toProtoStatusCode(span.getStatus().getStatusCode()));
    gen.writeEndObject();

    gen.writeEndObject();
  }

  private static void writeEvent(final JsonGenerator gen, final EventData event)
      throws IOException {
    gen.writeStartObject();
    gen.writeStringField("timeUnixNano", Long.toUnsignedString(event.getEpochNanos()));
    gen.writeStringField("name", event.getName());
    if (!event.getAttributes().isEmpty()) {
      gen.writeArrayFieldStart("attributes");
      writeAttributesEntries(gen, event.getAttributes(), true);
      gen.writeEndArray();
    }
    final int dropped = event.getTotalAttributeCount() - event.getAttributes().size();
    if (dropped > 0) {
      gen.writeNumberField("droppedAttributesCount", dropped);
    }
    gen.writeEndObject();
  }

  private static void writeLink(final JsonGenerator gen, final LinkData link) throws IOException {
    final var ctx = link.getSpanContext();
    gen.writeStartObject();
    gen.writeStringField("traceId", ctx.getTraceId());
    gen.writeStringField("spanId", ctx.getSpanId());
    final var traceState = formatTraceState(ctx.getTraceState());
    if (!traceState.isEmpty()) {
      gen.writeStringField("traceState", traceState);
    }
    if (!link.getAttributes().isEmpty()) {
      gen.writeArrayFieldStart("attributes");
      writeAttributesEntries(gen, link.getAttributes(), true);
      gen.writeEndArray();
    }
    final int dropped = link.getTotalAttributeCount() - link.getAttributes().size();
    if (dropped > 0) {
      gen.writeNumberField("droppedAttributesCount", dropped);
    }
    gen.writeNumberField("flags", encodeSpanFlags(ctx.getTraceFlags().asByte(), ctx.isRemote()));
    gen.writeEndObject();
  }

  private static void writeAttributesEntries(
      final JsonGenerator gen, final Attributes attributes, final boolean scrub)
      throws IOException {
    final var ioError = new IOException[1];
    attributes.forEach(
        (key, value) -> {
          if (ioError[0] != null || (scrub && isScrubbed(key.getKey()))) {
            return;
          }
          try {
            gen.writeStartObject();
            gen.writeStringField("key", key.getKey());
            gen.writeFieldName("value");
            writeAnyValue(gen, key, value);
            gen.writeEndObject();
          } catch (final IOException e) {
            ioError[0] = e;
          }
        });
    if (ioError[0] != null) {
      throw ioError[0];
    }
  }

  @SuppressWarnings("unchecked")
  private static void writeAnyValue(
      final JsonGenerator gen, final AttributeKey<?> key, final Object value) throws IOException {
    gen.writeStartObject();
    switch (key.getType()) {
      case STRING -> gen.writeStringField("stringValue", (String) value);
      case BOOLEAN -> gen.writeBooleanField("boolValue", (Boolean) value);
      case LONG -> gen.writeStringField("intValue", Long.toString((Long) value));
      case DOUBLE -> writeDoubleField(gen, (Double) value);
      case STRING_ARRAY -> {
        gen.writeObjectFieldStart("arrayValue");
        gen.writeArrayFieldStart("values");
        for (final var item : (List<String>) value) {
          gen.writeStartObject();
          if (item != null) {
            gen.writeStringField("stringValue", item);
          }
          gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
      }
      case BOOLEAN_ARRAY -> {
        gen.writeObjectFieldStart("arrayValue");
        gen.writeArrayFieldStart("values");
        for (final var item : (List<Boolean>) value) {
          gen.writeStartObject();
          if (item != null) {
            gen.writeBooleanField("boolValue", item);
          }
          gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
      }
      case LONG_ARRAY -> {
        gen.writeObjectFieldStart("arrayValue");
        gen.writeArrayFieldStart("values");
        for (final var item : (List<Long>) value) {
          gen.writeStartObject();
          if (item != null) {
            gen.writeStringField("intValue", Long.toString(item));
          }
          gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
      }
      case DOUBLE_ARRAY -> {
        gen.writeObjectFieldStart("arrayValue");
        gen.writeArrayFieldStart("values");
        for (final var item : (List<Double>) value) {
          gen.writeStartObject();
          if (item != null) {
            writeDoubleField(gen, item);
          }
          gen.writeEndObject();
        }
        gen.writeEndArray();
        gen.writeEndObject();
      }
      default -> gen.writeStringField("stringValue", String.valueOf(value));
    }
    gen.writeEndObject();
  }

  private static void writeDoubleField(final JsonGenerator gen, final double value)
      throws IOException {
    if (Double.isFinite(value)) {
      gen.writeNumberField("doubleValue", value);
    } else {
      gen.writeStringField("doubleValue", Double.toString(value));
    }
  }

  private static boolean isScrubbed(final String key) {
    final var lower = key.toLowerCase(Locale.ROOT);
    if (SCRUBBED_ATTRIBUTE_KEYS.contains(lower)) {
      return true;
    }
    for (final var prefix : SCRUBBED_ATTRIBUTE_PREFIXES) {
      if (lower.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String formatTraceState(final TraceState traceState) {
    if (traceState.isEmpty()) {
      return "";
    }
    final var sb = new StringBuilder();
    traceState.forEach(
        (k, v) -> {
          if (!sb.isEmpty()) {
            sb.append(',');
          }
          sb.append(k).append('=').append(v);
        });
    return sb.toString();
  }

  private static int encodeSpanFlags(final byte traceFlagsByte, final boolean isRemote) {
    // OTLP TraceFlags: lower 8 bits are W3C trace flags; bit 8 = has_is_remote; bit 9 = is_remote.
    return (traceFlagsByte & 0xFF) | 0x100 | (isRemote ? 0x200 : 0);
  }

  private static int toProtoSpanKind(final SpanKind kind) {
    return switch (kind) {
      case INTERNAL -> 1;
      case SERVER -> 2;
      case CLIENT -> 3;
      case PRODUCER -> 4;
      case CONSUMER -> 5;
    };
  }

  private static int toProtoStatusCode(final StatusCode statusCode) {
    return switch (statusCode) {
      case UNSET -> 0;
      case OK -> 1;
      case ERROR -> 2;
    };
  }

  private record ArraySlice(int offset, int length) {}

  public record SerializedNodeSpans(
      byte[] resourceSpansArrayJson, int exportedSpans, boolean truncated) {}
}
