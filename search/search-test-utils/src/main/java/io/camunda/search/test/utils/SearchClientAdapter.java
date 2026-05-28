/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.test.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.ilm.get_lifecycle.Lifecycle;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  public ElasticsearchClient getElsClient() {
    return elsClient;
  }

  private JsonNode opensearchIndexToNode(final IndexState index) {
    final Map<String, Object> indexAsMap;
    try {
      indexAsMap =
          schemaResourceSerializer.serialize(
              JacksonJsonpGenerator::new,
              (gen) -> index.serialize(gen, osClient._transport().jsonpMapper()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return objectMapper.valueToTree(indexAsMap);
  }

  private JsonNode elsIndexToNode(final co.elastic.clients.elasticsearch.indices.IndexState index) {
    final Map<String, Object> indexAsMap;
    try {
      indexAsMap =
          schemaResourceSerializer.serialize(
              co.elastic.clients.json.jackson.JacksonJsonpGenerator::new,
              (gen) -> index.serialize(gen, elsClient._jsonpMapper()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

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

  public Map<String, JsonNode> getAllIndicesAsNode(final String indexPrefix) throws IOException {
    if (elsClient != null) {
      final var indices = elsClient.indices().get(req -> req.index(indexPrefix + "*"));
      return indices.result().entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, e -> elsIndexToNode(e.getValue())));
    } else if (osClient != null) {
      final var indices = osClient.indices().get(req -> req.index(indexPrefix + "*"));
      return indices.result().entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, e -> opensearchIndexToNode(e.getValue())));
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

  public String getLifecyclePolicyNameForIndex(final String indexName) throws IOException {
    if (elsClient != null) {
      final var response = elsClient.indices().getSettings(req -> req.index(indexName));

      final var state = response.result().get(indexName);
      return Optional.ofNullable(state)
          .map(co.elastic.clients.elasticsearch.indices.IndexState::settings)
          .map(co.elastic.clients.elasticsearch.indices.IndexSettings::index)
          .map(co.elastic.clients.elasticsearch.indices.IndexSettings::lifecycle)
          .map(co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle::name)
          .orElse(null);
    } else if (osClient != null) {
      final var request =
          Requests.builder()
              .method("GET")
              .endpoint("_plugins/_ism/explain/" + indexName)
              .query(Map.of("show_policy", "true"))
              .build();

      try (final var response = osClient.generic().execute(request)) {
        final var json = objectMapper.readTree(response.getBody().get().body());
        return Optional.ofNullable(json.get(indexName))
            .filter(
                explain -> {
                  final var enabled = explain.get("enabled");
                  return enabled != null && enabled.asBoolean();
                })
            .map(explain -> explain.get("policy_id"))
            .map(JsonNode::asText)
            .orElse(null);
      }
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
    return get(id, null, index, classType);
  }

  public <T> T get(
      final String id, final String routing, final String index, final Class<T> classType)
      throws IOException {
    if (elsClient != null) {
      return elsClient.get(r -> r.id(id).routing(routing).index(index), classType).source();
    } else if (osClient != null) {
      return osClient.get(r -> r.id(id).routing(routing).index(index), classType).source();
    }
    return null;
  }

  public String index(final String id, final String index, final Object document)
      throws IOException {
    return index(id, null, index, document);
  }

  public String index(
      final String id, final String routing, final String index, final Object document)
      throws IOException {
    if (elsClient != null) {
      return elsClient
          .index(i -> i.index(index).id(id).routing(routing).document(document))
          .result()
          .jsonValue();
    } else if (osClient != null) {
      return osClient
          .index(i -> i.index(index).id(id).routing(routing).document(document))
          .result()
          .jsonValue();
    }
    return "";
  }

  public void createIndex(final String indexName, final int numberOfReplicas) throws IOException {
    if (elsClient != null) {
      elsClient
          .indices()
          .create(
              c ->
                  c.index(indexName)
                      .settings(
                          settings ->
                              settings
                                  .numberOfShards("1")
                                  .numberOfReplicas(String.valueOf(numberOfReplicas))));
    } else if (osClient != null) {
      osClient
          .indices()
          .create(
              c ->
                  c.index(indexName)
                      .settings(
                          settings ->
                              settings.numberOfShards(1).numberOfReplicas(numberOfReplicas)));
    }
  }

  public void deleteIndex(final String indexName) throws IOException {
    if (elsClient != null) {
      elsClient.indices().delete(d -> d.index(indexName));
    } else if (osClient != null) {
      osClient.indices().delete(d -> d.index(indexName));
    }
  }

  public void deleteIndexTemplate(final String templateName) throws IOException {
    if (elsClient != null) {
      elsClient.indices().deleteIndexTemplate(d -> d.name(templateName));
    } else if (osClient != null) {
      osClient.indices().deleteIndexTemplate(d -> d.name(templateName));
    }
  }

  public void refresh() throws IOException {
    if (elsClient != null) {
      elsClient.indices().refresh();
    } else if (osClient != null) {
      osClient.indices().refresh();
    }
  }

  /** Returns up to 10,000 documents from {@code index}. Suitable for test use only. */
  public <T> List<T> searchAll(final String index, final Class<T> classType) throws IOException {
    if (elsClient != null) {
      return elsClient
          .search(r -> r.index(index).size(10_000).query(q -> q.matchAll(m -> m)), classType)
          .hits()
          .hits()
          .stream()
          .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
          .toList();
    } else if (osClient != null) {
      return osClient
          .search(r -> r.index(index).size(10_000).query(q -> q.matchAll(m -> m)), classType)
          .hits()
          .hits()
          .stream()
          .map(org.opensearch.client.opensearch.core.search.Hit::source)
          .toList();
    }
    return List.of();
  }

  public <T> List<T> searchByIds(
      final String index, final List<String> ids, final Class<T> classType) throws IOException {
    if (elsClient != null) {
      return elsClient
          .search(
              r -> r.index(index).size(ids.size()).query(q -> q.ids(i -> i.values(ids))), classType)
          .hits()
          .hits()
          .stream()
          .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
          .toList();
    } else if (osClient != null) {
      return osClient
          .search(
              r -> r.index(index).size(ids.size()).query(q -> q.ids(i -> i.values(ids))), classType)
          .hits()
          .hits()
          .stream()
          .map(org.opensearch.client.opensearch.core.search.Hit::source)
          .toList();
    }
    return List.of();
  }

  /**
   * Deletes all documents from indices whose names start with {@code indexPrefix}. Scoped
   * deliberately to avoid affecting indices outside the prefix; never pass {@code "*"} here.
   */
  public void deleteAllDocuments(final String indexPrefix) throws IOException {
    if (elsClient != null) {
      elsClient.deleteByQuery(
          d ->
              d.index(indexPrefix + "*")
                  .query(q -> q.matchAll(m -> m))
                  .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed));
    } else if (osClient != null) {
      osClient.deleteByQuery(
          d ->
              d.index(indexPrefix + "*")
                  .query(q -> q.matchAll(m -> m))
                  .conflicts(org.opensearch.client.opensearch._types.Conflicts.Proceed));
    }
  }

  private static final class SchemaResourceSerializer {

    private final ObjectMapper objectMapper;

    public SchemaResourceSerializer(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    public Map<String, Object> serialize(
        final Function<JsonGenerator, jakarta.json.stream.JsonGenerator> jacksonGenerator,
        final Consumer<jakarta.json.stream.JsonGenerator> serialize)
        throws IOException {
      try (final var out = new StringWriter();
          final var jsonGenerator = new JsonFactory().createGenerator(out);
          final jakarta.json.stream.JsonGenerator jacksonJsonpGenerator =
              jacksonGenerator.apply(jsonGenerator)) {
        serialize.accept(jacksonJsonpGenerator);
        jacksonJsonpGenerator.flush();

        return objectMapper.readValue(out.toString(), new TypeReference<>() {});
      }
    }
  }
}
