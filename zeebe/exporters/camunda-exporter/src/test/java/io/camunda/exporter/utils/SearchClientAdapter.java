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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.SchemaResourceSerializer;
import java.io.IOException;
import java.util.Objects;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.IndexState;

public class SearchClientAdapter {

  private final ElasticsearchClient elsClient;
  private final OpenSearchClient osClient;
  private final SchemaResourceSerializer schemaResourceSerializer;
  private final ObjectMapper objectMapper;

  public SearchClientAdapter(final ElasticsearchClient elsClient, final ObjectMapper objectMapper) {
    Objects.requireNonNull(elsClient, "elsClient cannot be null");
    this.elsClient = elsClient;
    osClient = null;
    this.objectMapper = objectMapper;
    schemaResourceSerializer = new SchemaResourceSerializer(objectMapper);
  }

  public SearchClientAdapter(final OpenSearchClient osClient, final ObjectMapper objectMapper) {
    Objects.requireNonNull(osClient, "osClient cannot be null");
    elsClient = null;
    this.objectMapper = objectMapper;
    this.osClient = osClient;
    schemaResourceSerializer = new SchemaResourceSerializer(objectMapper);
  }

  private JsonNode opensearchIndexToNode(final IndexState index) throws IOException {
    final var indexAsMap =
        schemaResourceSerializer.serialize(
            JacksonJsonpGenerator::new,
            (gen) -> index.serialize(gen, osClient._transport().jsonpMapper()));

    return objectMapper.valueToTree(indexAsMap);
  }

  private JsonNode elsIndexToNode(final co.elastic.clients.elasticsearch.indices.IndexState index)
      throws IOException {
    final var indexAsMap =
        schemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> index.serialize(gen, elsClient._jsonpMapper()));

    return objectMapper.valueToTree(indexAsMap);
  }

  private JsonNode elsIndexTemplateToNode(final IndexTemplateItem indexTemplate)
      throws IOException {
    final var templateAsMap =
        schemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> indexTemplate.serialize(gen, elsClient._jsonpMapper()));

    return objectMapper.valueToTree(templateAsMap);
  }

  private JsonNode opensearchIndexTemplateToNode(
      final org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem
          indexTemplate)
      throws IOException {
    final var templateAsMap =
        schemaResourceSerializer.serialize(
            JacksonJsonpGenerator::new,
            (gen) -> indexTemplate.serialize(gen, osClient._transport().jsonpMapper()));

    return objectMapper.valueToTree(templateAsMap);
  }

  private JsonNode elsPolicyToNode(final Lifecycle lifecyclePolicy) throws IOException {
    final var policyAsMap =
        schemaResourceSerializer.serialize(
            co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
            (gen) -> lifecyclePolicy.serialize(gen, elsClient._jsonpMapper()));

    return objectMapper.valueToTree(policyAsMap);
  }

  public JsonNode getIndexAsNode(final String indexName) throws IOException {
    if (elsClient != null) {
      final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);
      return elsIndexToNode(index);
    } else if (osClient != null) {
      final var index = osClient.indices().get(req -> req.index(indexName)).get(indexName);
      return opensearchIndexToNode(index);
    }
    return null;
  }

  public JsonNode getPolicyAsNode(final String policyName) throws IOException {
    if (elsClient != null) {
      final var policy =
          elsClient.ilm().getLifecycle(req -> req.name(policyName)).result().get(policyName);

      return elsPolicyToNode(policy);
    } else if (osClient != null) {
      final var request =
          Requests.builder().method("GET").endpoint("_plugins/_ism/policies/" + policyName).build();

      return objectMapper.readTree(osClient.generic().execute(request).getBody().get().body());
    }
    return null;
  }

  public JsonNode getIndexTemplateAsNode(final String templateName) throws IOException {
    if (elsClient != null) {
      final var template =
          elsClient
              .indices()
              .getIndexTemplate(req -> req.name(templateName))
              .indexTemplates()
              .getFirst();
      return elsIndexTemplateToNode(template);
    } else if (osClient != null) {
      final var template =
          osClient
              .indices()
              .getIndexTemplate(req -> req.name(templateName))
              .indexTemplates()
              .getFirst();

      return opensearchIndexTemplateToNode(template);
    }
    return null;
  }

  public <T> T get(final String id, final String index, final Class<T> classType)
      throws IOException {
    if (elsClient != null) {
      return elsClient.get(r -> r.id(id).index(index), classType).source();
    } else if (osClient != null) {
      return osClient.get(r -> r.id(id).index(index), classType).source();
    }
    return null;
  }

  public String index(final String id, final String index, final Object document)
      throws IOException {
    if (elsClient != null) {
      return elsClient.index(i -> i.index(index).id(id).document(document)).result().jsonValue();
    } else if (osClient != null) {
      return osClient.index(i -> i.index(index).id(id).document(document)).result().jsonValue();
    }
    return "";
  }

  public void refresh() throws IOException {
    if (elsClient != null) {
      elsClient.indices().refresh();
    } else if (osClient != null) {
      osClient.indices().refresh();
    }
  }
}
