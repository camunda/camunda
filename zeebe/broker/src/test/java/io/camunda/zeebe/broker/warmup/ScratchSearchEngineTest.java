/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ScratchSearchEngineTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient client = HttpClient.newHttpClient();
  private ScratchSearchEngine searchEngine;

  @AfterEach
  void tearDown() throws Exception {
    client.close();
    if (searchEngine != null) {
      searchEngine.close();
    }
  }

  @Test
  void shouldAcknowledgeEveryBulkOperation() throws Exception {
    // given
    searchEngine = new ScratchSearchEngine();
    final var bulk =
        """
        {"index":{"_index":"process","_id":"1"}}
        {"key":1}
        {"update":{"_index":"list-view","_id":"2"}}
        {"doc":{"state":"ACTIVE"},"upsert":{"state":"ACTIVE"}}
        {"delete":{"_index":"list-view","_id":"3"}}
        """;

    // when
    final var response = send("POST", "/_bulk", bulk);

    // then
    assertThat(response.headers().firstValue("X-Elastic-Product")).hasValue("Elasticsearch");
    final var body = MAPPER.readTree(response.body());
    assertThat(body.get("errors").asBoolean()).isFalse();
    assertThat(body.get("items"))
        .extracting(item -> item.fieldNames().next(), ScratchSearchEngineTest::id)
        .containsExactly(tuple("index", "1"), tuple("update", "2"), tuple("delete", "3"));
    assertThat(searchEngine.unrecognisedRequests()).isZero();
  }

  @Test
  void shouldAnswerSearchesWithAnEmptyResultForEachAggregation() throws Exception {
    // given
    searchEngine = new ScratchSearchEngine();
    final var search =
        """
        {"size":0,"aggs":{
          "ids":{"terms":{"field":"id"}},
          "pending":{"filter":{"term":{"state":"PENDING"}},"aggs":{"total":{"sum":{"field":"n"}}}}
        }}
        """;

    // when
    final var response = send("POST", "/operation/_search?typed_keys=true", search);

    // then
    final var body = MAPPER.readTree(response.body());
    assertThat(body.at("/hits/hits")).isEmpty();
    assertThat(body.at("/aggregations/sterms#ids/buckets")).isEmpty();
    assertThat(body.at("/aggregations/filter#pending/doc_count").asInt()).isZero();
    assertThat(body.at("/aggregations/filter#pending/sum#total/value").isNumber()).isTrue();
    assertThat(searchEngine.unrecognisedRequests()).isZero();
  }

  @Test
  void shouldAnswerInTheCompatibleMediaTypeTheClientAsksFor() throws Exception {
    // given
    searchEngine = new ScratchSearchEngine();

    // when
    final var response =
        client.send(
            HttpRequest.newBuilder(URI.create(searchEngine.url() + "/_bulk"))
                .POST(BodyPublishers.ofString("{\"delete\":{\"_index\":\"a\",\"_id\":\"1\"}}\n"))
                .header("Accept", "application/vnd.elasticsearch+json; compatible-with=8")
                .build(),
            BodyHandlers.ofString());

    // then
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValue("application/vnd.elasticsearch+json;compatible-with=8");
  }

  @Test
  void shouldCountRequestsItDoesNotRecognise() throws Exception {
    // given
    searchEngine = new ScratchSearchEngine();

    // when
    final var response = send("GET", "/_cat/indices", "");

    // then
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(searchEngine.requests()).isOne();
    assertThat(searchEngine.unrecognisedRequests()).isOne();
  }

  private HttpResponse<String> send(final String method, final String path, final String body)
      throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create(searchEngine.url() + path))
            .method(method, BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build(),
        BodyHandlers.ofString());
  }

  private static String id(final JsonNode item) {
    return item.elements().next().get("_id").asText();
  }
}
