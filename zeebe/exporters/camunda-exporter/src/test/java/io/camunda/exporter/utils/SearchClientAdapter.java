/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.ilm.get_lifecycle.Lifecycle;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.SchemaResourceSerializer;
import java.io.IOException;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.IndexState;

public class SearchClientAdapter {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonpMapper ELS_JSON_MAPPER =
      new co.elastic.clients.json.jackson.JacksonJsonpMapper(MAPPER);
  private static final org.opensearch.client.json.JsonpMapper OPENSEARCH_JSON_MAPPER =
      new JacksonJsonpMapper(MAPPER);

  private final ElasticsearchClient elsClient;
  private final OpenSearchClient osClient;

  public SearchClientAdapter(final ElasticsearchClient elsClient) {
    this.elsClient = elsClient;
    osClient = null;
  }

  public SearchClientAdapter(final OpenSearchClient osClient) {
    elsClient = null;
    this.osClient = osClient;
  }

  private JsonNode opensearchIndexToNode(final IndexState index) throws IOException {
    final var indexAsMap =
        SchemaResourceSerializer.serialize(
            JacksonJsonpGenerator::new, (gen) -> index.serialize(gen, OPENSEARCH_JSON_MAPPER));

    return MAPPER.valueToTree(indexAsMap);
  }

  private JsonNode elsIndexToNode(final co.elastic.clients.elasticsearch.indices.IndexState index)
      throws IOException {
    final var indexAsMap =
        SchemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> index.serialize(gen, ELS_JSON_MAPPER));

    return MAPPER.valueToTree(indexAsMap);
  }

  private JsonNode elsIndexTemplateToNode(final IndexTemplateItem indexTemplate)
      throws IOException {
    final var templateAsMap =
        SchemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> indexTemplate.serialize(gen, ELS_JSON_MAPPER));

    return MAPPER.valueToTree(templateAsMap);
  }

  private JsonNode opensearchIndexTemplateToNode(
      final org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem
          indexTemplate)
      throws IOException {
    final var templateAsMap =
        SchemaResourceSerializer.serialize(
            JacksonJsonpGenerator::new,
            (gen) -> indexTemplate.serialize(gen, OPENSEARCH_JSON_MAPPER));

    return MAPPER.valueToTree(templateAsMap);
  }

  private JsonNode elsPolicyToNode(final Lifecycle lifecyclePolicy) throws IOException {
    final var policyAsMap =
        SchemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> lifecyclePolicy.serialize(gen, ELS_JSON_MAPPER));

    return MAPPER.valueToTree(policyAsMap);
  }

  public JsonNode getIndexAsNode(final String indexName) throws IOException {
    switch (elsClient != null ? "elsClient" : "osClient") {
      case "elsClient" -> {
        final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);
        return elsIndexToNode(index);
      }
      case "osClient" -> {
        final var index = osClient.indices().get(req -> req.index(indexName)).get(indexName);
        return opensearchIndexToNode(index);
      }
      default -> throw new IllegalStateException("Must instantiate client in SearchClientAdapter");
    }
  }

  public JsonNode getPolicyAsNode(final String policyName) throws IOException {
    switch (elsClient != null ? "elsClient" : "osClient") {
      case "elsClient" -> {
        final var policy =
            elsClient.ilm().getLifecycle(req -> req.name(policyName)).result().get(policyName);

        return elsPolicyToNode(policy);
      }
      case "osClient" -> {
        final var request =
            Requests.builder()
                .method("GET")
                .endpoint("_plugins/_ism/policies/" + policyName)
                .build();

        return MAPPER.readTree(osClient.generic().execute(request).getBody().get().body());
      }
      default -> throw new IllegalStateException("Must instantiate client in SearchClientAdapter");
    }
  }

  public JsonNode getIndexTemplateAsNode(final String templateName) throws IOException {
    switch (elsClient != null ? "elsClient" : "osClient") {
      case "elsClient" -> {
        final var template =
            elsClient
                .indices()
                .getIndexTemplate(req -> req.name(templateName))
                .indexTemplates()
                .getFirst();
        return elsIndexTemplateToNode(template);
      }
      case "osClient" -> {
        final var template =
            osClient
                .indices()
                .getIndexTemplate(req -> req.name(templateName))
                .indexTemplates()
                .getFirst();

        return opensearchIndexTemplateToNode(template);
      }
      default -> throw new IllegalStateException("Must instantiate client in SearchClientAdapter");
    }
  }

  public <T> T get(final String id, final String index, final Class<T> classType)
      throws IOException {
    switch (elsClient != null ? "elsClient" : "osClient") {
      case "elsClient" -> {
        return elsClient.get(r -> r.id(id).index(index), classType).source();
      }
      case "osClient" -> {
        return osClient.get(r -> r.id(id).index(index), classType).source();
      }
      default -> throw new IllegalStateException("Must instantiate client in SearchClientAdapter");
    }
  }
}
