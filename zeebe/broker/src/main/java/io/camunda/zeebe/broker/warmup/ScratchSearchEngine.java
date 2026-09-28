/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A loopback HTTP endpoint that answers the Elasticsearch APIs the exporters use, so that the
 * warm-up's exporters run their real client, serialisation and response handling without anything
 * leaving the broker. Every write succeeds and is discarded; every read finds nothing. Requests it
 * does not recognise are acknowledged and counted.
 */
@NullMarked
final class ScratchSearchEngine implements AutoCloseable {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonFactory JSON = MAPPER.getFactory();
  private static final int MAX_LOGGED_UNRECOGNISED = 20;
  private static final String COMPATIBLE_MEDIA_TYPE = "application/vnd.elasticsearch+json";
  private static final Set<String> BY_QUERY_APIS =
      Set.of("_delete_by_query", "_update_by_query", "_reindex");
  private static final Set<String> ACKNOWLEDGED_APIS =
      Set.of("_settings", "_alias", "_aliases", "_mapping", "_ilm", "_index_template");
  private static final Set<String> SINGLE_BUCKET_AGGREGATIONS =
      Set.of("filter", "nested", "reverse_nested", "global", "missing", "children", "parent");
  private static final Map<String, String> MULTI_BUCKET_AGGREGATIONS =
      Map.of(
          "terms", "sterms",
          "composite", "composite",
          "multi_terms", "multi_terms",
          "date_histogram", "date_histogram",
          "histogram", "histogram",
          "range", "range",
          "date_range", "date_range");
  private static final Set<String> VALUE_AGGREGATIONS =
      Set.of("sum", "min", "max", "avg", "value_count", "cardinality");

  private final HttpServer server;
  private final ExecutorService executor;
  private final LongAdder requests = new LongAdder();
  private final LongAdder unrecognised = new LongAdder();
  private final Set<String> loggedUnrecognised = ConcurrentHashMap.newKeySet();

  ScratchSearchEngine() throws IOException {
    final var threads = new AtomicInteger();
    executor =
        Executors.newFixedThreadPool(
            2,
            runnable -> {
              final var thread =
                  new Thread(runnable, "zeebe-leader-warmup-search-" + threads.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
  }

  String url() {
    return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
  }

  long requests() {
    return requests.sum();
  }

  long unrecognisedRequests() {
    return unrecognised.sum();
  }

  @Override
  public void close() throws InterruptedException {
    server.stop(0);
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
  }

  private void handle(final HttpExchange exchange) throws IOException {
    requests.increment();
    try (exchange) {
      final var method = exchange.getRequestMethod();
      final var path = exchange.getRequestURI().getPath();
      final var query = exchange.getRequestURI().getRawQuery();
      final var body = readBody(exchange);
      final var segments = List.of(path.replaceFirst("^/+", "").split("/"));
      final var out = new ByteArrayOutputStream();
      int status = 200;
      try (final var json = JSON.createGenerator(out)) {
        final var api = api(segments);
        if ("_bulk".equals(api)) {
          bulk(body, json);
        } else if ("_search".equals(api) && !"DELETE".equals(method)) {
          search(body, query, json);
        } else if ("_count".equals(api)) {
          json.writeStartObject();
          json.writeNumberField("count", 0);
          shards(json);
          json.writeEndObject();
        } else if (BY_QUERY_APIS.contains(api)) {
          byQuery(json);
        } else if ("_doc".equals(api) || "_source".equals(api)) {
          status = "GET".equals(method) || "HEAD".equals(method) ? 404 : 201;
          document(segments, status == 404, json);
        } else if ("_mget".equals(api)) {
          multiGet(body, json);
        } else if ("_refresh".equals(api) || "_flush".equals(api)) {
          json.writeStartObject();
          shards(json);
          json.writeEndObject();
        } else if ("_pit".equals(api) || "_search".equals(api)) {
          json.writeStartObject();
          if ("DELETE".equals(method)) {
            json.writeBooleanField("succeeded", true);
            json.writeNumberField("num_freed", 1);
          } else {
            json.writeStringField("id", "leader-warmup");
          }
          json.writeEndObject();
        } else {
          if (!ACKNOWLEDGED_APIS.contains(api)) {
            onUnrecognised(method, path);
          }
          json.writeStartObject();
          json.writeBooleanField("acknowledged", true);
          json.writeEndObject();
        }
      } catch (final IOException | RuntimeException e) {
        LeaderWarmup.LOG.debug("Leader warm-up search engine failed to answer {}", path, e);
        onUnrecognised(method, path);
        status = 500;
        out.reset();
        out.write(
            "{\"error\":{\"type\":\"leader_warmup\",\"reason\":\"unsupported request\"},\"status\":500}"
                .getBytes(StandardCharsets.UTF_8));
      }
      respond(exchange, status, "HEAD".equals(method) ? new byte[0] : out.toByteArray());
    }
  }

  private void onUnrecognised(final String method, final String path) {
    unrecognised.increment();
    final var route = method + " " + path.replaceAll("/[^/_][^/]*", "/*");
    if (loggedUnrecognised.size() < MAX_LOGGED_UNRECOGNISED && loggedUnrecognised.add(route)) {
      LeaderWarmup.LOG.debug("Leader warm-up search engine does not recognise {}", route);
    }
  }

  /** The API a path addresses: its last segment that starts with an underscore, if any. */
  private static String api(final List<String> segments) {
    for (int i = segments.size() - 1; i >= 0; i--) {
      if (segments.get(i).startsWith("_")) {
        return segments.get(i);
      }
    }
    return "";
  }

  private static void bulk(final byte[] body, final JsonGenerator json) throws IOException {
    json.writeStartObject();
    json.writeNumberField("took", 0);
    json.writeBooleanField("errors", false);
    json.writeArrayFieldStart("items");
    final var lines = new String(body, StandardCharsets.UTF_8).split("\n");
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].isBlank()) {
        continue;
      }
      final var action = MAPPER.readTree(lines[i]);
      final var operation = action.fieldNames().next();
      final var metadata = action.get(operation);
      if (!"delete".equals(operation)) {
        i++;
      }
      json.writeStartObject();
      json.writeObjectFieldStart(operation);
      json.writeStringField("_index", text(metadata, "_index", "leader-warmup"));
      json.writeStringField("_id", text(metadata, "_id", Integer.toString(i)));
      json.writeNumberField("_version", 1);
      json.writeStringField(
          "result",
          switch (operation) {
            case "delete" -> "deleted";
            case "update" -> "updated";
            default -> "created";
          });
      shards(json);
      json.writeNumberField("_seq_no", 0);
      json.writeNumberField("_primary_term", 1);
      json.writeNumberField(
          "status", "index".equals(operation) || "create".equals(operation) ? 201 : 200);
      json.writeEndObject();
      json.writeEndObject();
    }
    json.writeEndArray();
    json.writeEndObject();
  }

  private void search(final byte[] body, final @Nullable String query, final JsonGenerator json)
      throws IOException {
    final var request = body.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(body);
    json.writeStartObject();
    if (query != null && query.contains("scroll=")) {
      json.writeStringField("_scroll_id", "leader-warmup");
    }
    if (request.has("pit")) {
      json.writeStringField("pit_id", "leader-warmup");
    }
    json.writeNumberField("took", 0);
    json.writeBooleanField("timed_out", false);
    shards(json);
    json.writeObjectFieldStart("hits");
    emptyHits(json);
    json.writeEndObject();
    final var aggregations = aggregations(request);
    if (aggregations != null) {
      json.writeObjectFieldStart("aggregations");
      writeAggregations(aggregations, json);
      json.writeEndObject();
    }
    json.writeEndObject();
  }

  /**
   * Writes an empty result for every requested aggregation, keyed as {@code typed_keys} would key
   * it, so that the client can read each one back as the type it asked for.
   */
  private void writeAggregations(final JsonNode aggregations, final JsonGenerator json)
      throws IOException {
    for (final var entry : aggregations.properties()) {
      final var definition = entry.getValue();
      @Nullable String type = null;
      for (final var field : definition.properties()) {
        if (!"aggs".equals(field.getKey())
            && !"aggregations".equals(field.getKey())
            && !"meta".equals(field.getKey())) {
          type = field.getKey();
          break;
        }
      }
      if (type == null) {
        onUnrecognised("AGGREGATION", "(none)");
        continue;
      }

      if (SINGLE_BUCKET_AGGREGATIONS.contains(type)) {
        json.writeObjectFieldStart(type + "#" + entry.getKey());
        json.writeNumberField("doc_count", 0);
        final var nested = aggregations(definition);
        if (nested != null) {
          writeAggregations(nested, json);
        }
      } else if (MULTI_BUCKET_AGGREGATIONS.containsKey(type)) {
        json.writeObjectFieldStart(MULTI_BUCKET_AGGREGATIONS.get(type) + "#" + entry.getKey());
        if ("terms".equals(type)) {
          json.writeNumberField("doc_count_error_upper_bound", 0);
          json.writeNumberField("sum_other_doc_count", 0);
        }
        json.writeArrayFieldStart("buckets");
        json.writeEndArray();
      } else if (VALUE_AGGREGATIONS.contains(type)) {
        json.writeObjectFieldStart(type + "#" + entry.getKey());
        if ("min".equals(type) || "max".equals(type) || "avg".equals(type)) {
          json.writeNullField("value");
        } else {
          json.writeNumberField("value", 0);
        }
      } else if ("top_hits".equals(type)) {
        json.writeObjectFieldStart(type + "#" + entry.getKey());
        json.writeObjectFieldStart("hits");
        emptyHits(json);
        json.writeEndObject();
      } else {
        onUnrecognised("AGGREGATION", type);
        continue;
      }
      json.writeEndObject();
    }
  }

  private static @Nullable JsonNode aggregations(final JsonNode node) {
    final var aggregations = node.get("aggregations");
    return aggregations != null ? aggregations : node.get("aggs");
  }

  private static void byQuery(final JsonGenerator json) throws IOException {
    json.writeStartObject();
    json.writeNumberField("took", 0);
    json.writeBooleanField("timed_out", false);
    for (final var field :
        List.of(
            "total", "created", "updated", "deleted", "batches", "version_conflicts", "noops")) {
      json.writeNumberField(field, 0);
    }
    json.writeObjectFieldStart("retries");
    json.writeNumberField("bulk", 0);
    json.writeNumberField("search", 0);
    json.writeEndObject();
    json.writeNumberField("throttled_millis", 0);
    json.writeNumberField("requests_per_second", -1.0);
    json.writeNumberField("throttled_until_millis", 0);
    json.writeArrayFieldStart("failures");
    json.writeEndArray();
    json.writeEndObject();
  }

  private static void document(
      final List<String> segments, final boolean read, final JsonGenerator json)
      throws IOException {
    final var index = segments.getFirst();
    final var id = segments.size() > 2 ? segments.get(2) : "leader-warmup";
    json.writeStartObject();
    json.writeStringField("_index", index);
    json.writeStringField("_id", id);
    if (read) {
      json.writeBooleanField("found", false);
    } else {
      json.writeNumberField("_version", 1);
      json.writeStringField("result", "created");
      shards(json);
      json.writeNumberField("_seq_no", 0);
      json.writeNumberField("_primary_term", 1);
    }
    json.writeEndObject();
  }

  private static void multiGet(final byte[] body, final JsonGenerator json) throws IOException {
    final var request = MAPPER.readTree(body);
    json.writeStartObject();
    json.writeArrayFieldStart("docs");
    final var docs = request.get("docs");
    if (docs != null) {
      for (final var doc : docs) {
        writeMissingDocument(text(doc, "_index", "leader-warmup"), text(doc, "_id", ""), json);
      }
    }
    final var ids = request.get("ids");
    if (ids != null) {
      for (final var id : ids) {
        writeMissingDocument("leader-warmup", id.asText(), json);
      }
    }
    json.writeEndArray();
    json.writeEndObject();
  }

  private static void writeMissingDocument(
      final String index, final String id, final JsonGenerator json) throws IOException {
    json.writeStartObject();
    json.writeStringField("_index", index);
    json.writeStringField("_id", id);
    json.writeBooleanField("found", false);
    json.writeEndObject();
  }

  private static void emptyHits(final JsonGenerator json) throws IOException {
    json.writeObjectFieldStart("total");
    json.writeNumberField("value", 0);
    json.writeStringField("relation", "eq");
    json.writeEndObject();
    json.writeNullField("max_score");
    json.writeArrayFieldStart("hits");
    json.writeEndArray();
  }

  private static void shards(final JsonGenerator json) throws IOException {
    json.writeObjectFieldStart("_shards");
    json.writeNumberField("total", 1);
    json.writeNumberField("successful", 1);
    json.writeNumberField("skipped", 0);
    json.writeNumberField("failed", 0);
    json.writeEndObject();
  }

  private static String text(final JsonNode node, final String field, final String fallback) {
    final var value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : fallback;
  }

  private static byte[] readBody(final HttpExchange exchange) throws IOException {
    final var encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
    try (final InputStream body =
        encoding != null && encoding.equalsIgnoreCase("gzip")
            ? new GZIPInputStream(exchange.getRequestBody())
            : exchange.getRequestBody()) {
      return body.readAllBytes();
    }
  }

  /**
   * Elasticsearch answers a client that asks for a compatible-with media type in that media type,
   * which the client checks for after plain JSON.
   */
  private static String contentType(final @Nullable String accept) {
    if (accept != null && accept.startsWith(COMPATIBLE_MEDIA_TYPE)) {
      final var version = accept.replaceFirst(".*compatible-with=\\s*(\\d+).*", "$1");
      return COMPATIBLE_MEDIA_TYPE + ";compatible-with=" + version;
    }
    return "application/json";
  }

  private static void respond(final HttpExchange exchange, final int status, final byte[] body)
      throws IOException {
    final var headers = exchange.getResponseHeaders();
    headers.set("Content-Type", contentType(exchange.getRequestHeaders().getFirst("Accept")));
    headers.set("X-Elastic-Product", "Elasticsearch");
    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    if (body.length > 0) {
      exchange.getResponseBody().write(body);
    }
  }
}
