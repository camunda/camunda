/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

public class ElasticsearchSchemaVersionChecker {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String indexName = "index-template-created";
  private final RestClient client;

  public ElasticsearchSchemaVersionChecker(final RestClient client) {
    this.client = client;
  }

  public boolean contains(final String value) {
    try {
      final var encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
      final var request = new Request("GET", "/" + indexName + "/_doc/" + encodedValue);
      final var response = client.performRequest(request);
      return response.getStatusLine().getStatusCode() == 200;
    } catch (final Exception e) {
      return false;
    }
  }

  public void add(final String value) {
    try {
      final var doc = Map.of("value", value);
      final var encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
      final var request = new Request("PUT", "/" + indexName + "/_doc/" + encodedValue);
      request.setJsonEntity(MAPPER.writeValueAsString(doc));
      client.performRequest(request);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to add value: " + value, e);
    }
  }
}
