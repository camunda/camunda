/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prototype read side for the variable history: queries the {@code variable-history} index
 * directly, without the search domain layers, authorization or RDBMS support.
 */
@RestController
@ConditionalOnRestGatewayEnabled
@ConditionalOnSecondaryStorageType(SecondaryStorageType.elasticsearch)
@RequestMapping("/v2/variables/history")
public class VariableHistoryPrototypeController {

  private static final String INDEX_PATTERN = "*variable-history*";
  private static final int MAX_ITEMS = 1000;

  private final ElasticsearchClient client;
  private final Environment environment;

  public VariableHistoryPrototypeController(
      final ElasticsearchClient client, final Environment environment) {
    this.client = client;
    this.environment = environment;
  }

  private Map<String, Boolean> tracing() {
    return Map.of(
        "user", flag("user", true),
        "client", flag("client", false),
        "engine", flag("engine", false));
  }

  private boolean flag(final String actor, final boolean defaultValue) {
    return environment.getProperty(
        "camunda.data.audit-log.variable-history-" + actor, Boolean.class, defaultValue);
  }

  @PostMapping("/search")
  public Map<String, Object> search(@RequestBody final Map<String, Object> request)
      throws IOException {
    final Map<?, ?> filter =
        request.get("filter") instanceof final Map<?, ?> given ? given : Map.of();
    final List<Query> must = new ArrayList<>();
    termFilter(must, "processInstanceKey", filter);
    termFilter(must, "scopeKey", filter);
    termFilter(must, "name", filter);

    final var response =
        client.search(
            s ->
                s.index(INDEX_PATTERN)
                    .size(MAX_ITEMS)
                    .query(q -> q.bool(b -> b.must(must)))
                    .sort(o -> o.field(f -> f.field("timestamp").order(SortOrder.Asc)))
                    .sort(o -> o.field(f -> f.field("position").order(SortOrder.Asc))),
            Map.class);

    final List<Map<String, Object>> items = new ArrayList<>();
    Map<?, ?> previous = null;
    for (final Hit<Map> hit : response.hits().hits()) {
      final Map<?, ?> source = hit.source();
      if (source != null) {
        items.add(toItem(source, previous));
        previous = source;
      }
    }
    return Map.of(
        "items",
        items,
        "tracing",
        tracing(),
        "page",
        Map.of(
            "totalItems", items.size(),
            "hasMoreTotalItems", false,
            "startCursor", "",
            "endCursor", ""));
  }

  @GetMapping("/{key}")
  public ResponseEntity<Map<String, Object>> get(@PathVariable("key") final String key)
      throws IOException {
    final var response =
        client.search(
            s -> s.index(INDEX_PATTERN).size(1).query(q -> q.term(t -> t.field("id").value(key))),
            Map.class);
    return response.hits().hits().stream()
        .findFirst()
        .map(Hit::source)
        .map(source -> ResponseEntity.ok(toItem(source, null)))
        .orElse(ResponseEntity.notFound().build());
  }

  private static void termFilter(
      final List<Query> must, final String field, final Map<?, ?> filter) {
    final Object value = filter.get(field);
    if (value != null) {
      must.add(Query.of(q -> q.term(t -> t.field(field).value(String.valueOf(value)))));
    }
  }

  private static Map<String, Object> toItem(final Map<?, ?> source, final Map<?, ?> previous) {
    final Map<String, Object> item = new LinkedHashMap<>();
    item.put("variableHistoryItemKey", source.get("id"));
    item.put("variableKey", String.valueOf(source.get("scopeKey")) + ":" + source.get("name"));
    item.put("name", source.get("name"));
    item.put("value", fullValue(source));
    item.put("isTruncated", false);
    item.put("previousValue", previous == null ? null : fullValue(previous));
    item.put("isPreviousValueTruncated", false);
    item.put("metadata", Map.of("protectionModes", List.of()));
    item.put("scopeKey", String.valueOf(source.get("scopeKey")));
    item.put("elementInstanceKey", null);
    item.put("elementId", null);
    item.put("processInstanceKey", String.valueOf(source.get("processInstanceKey")));
    item.put(
        "rootProcessInstanceKey",
        String.valueOf(
            source.get("rootProcessInstanceKey") != null
                ? source.get("rootProcessInstanceKey")
                : source.get("processInstanceKey")));
    item.put("processDefinitionKey", String.valueOf(source.get("processDefinitionKey")));
    item.put("processDefinitionId", source.get("bpmnProcessId"));
    item.put("tenantId", source.get("tenantId"));
    item.put("timestamp", toIso(source.get("timestamp")));
    item.put("operationType", source.get("operation"));
    item.put("actorType", source.get("actorType"));
    item.put("actorId", source.get("actorId"));
    item.put("agentElementId", null);
    return item;
  }

  private static Object fullValue(final Map<?, ?> source) {
    return source.get("fullValue") != null ? source.get("fullValue") : source.get("value");
  }

  private static String toIso(final Object timestamp) {
    return timestamp instanceof final Number number
        ? Instant.ofEpochMilli(number.longValue()).toString()
        : String.valueOf(timestamp);
  }
}
