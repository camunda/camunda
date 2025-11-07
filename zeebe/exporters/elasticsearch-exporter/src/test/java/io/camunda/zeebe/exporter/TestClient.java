/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

/**
 * A thin client to verify properties from Elastic. Wraps both the low and high level clients from
 * Elastic in a closeable resource.
 */
final class TestClient implements CloseableSilently {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private final ElasticsearchExporterConfiguration config;
  private final RestClient restClient;
  private final ElasticsearchClient esClient;
  private final RecordIndexRouter indexRouter;

  TestClient(final ElasticsearchExporterConfiguration config, final RecordIndexRouter indexRouter) {
    this.config = config;
    this.indexRouter = indexRouter;

    restClient = RestClientFactory.of(config);

    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(MAPPER));
    esClient = new ElasticsearchClient(transport);
  }

  TestClient(
      final ElasticsearchExporterConfiguration config,
      final RecordIndexRouter indexRouter,
      final RestClient restClient) {
    this.config = config;
    this.indexRouter = indexRouter;

    this.restClient = restClient;

    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(MAPPER));
    esClient = new ElasticsearchClient(transport);
  }

  @SuppressWarnings("rawtypes")
  GetResponse<Record> getExportedDocumentFor(final Record<?> record) {
    final var indexName = indexRouter.indexFor(record);

    try {
      esClient.indices().refresh(b -> b.index(indexName)); // ensure latest data is visible
      return esClient.get(b -> b.id(indexRouter.idFor(record)).index(indexName), Record.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<IndexTemplateWrapper> getIndexTemplate(final ValueType valueType, final String version) {
    try {
      final var request =
          new Request(
              "GET", "/_index_template/" + indexRouter.indexPrefixForValueType(valueType, version));
      final var response = restClient.performRequest(request);
      final var templates =
          MAPPER.readValue(response.getEntity().getContent(), IndexTemplatesDto.class);
      return templates.wrappers().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<ComponentTemplateWrapper> getComponentTemplate() {
    try {
      final var request =
          new Request(
              "GET",
              "/_component_template/"
                  + config.index.prefix
                  + "-"
                  + VersionUtil.getVersionLowerCase());
      final var response = restClient.performRequest(request);
      final var templates =
          MAPPER.readValue(response.getEntity().getContent(), ComponentTemplatesDto.class);
      return templates.wrappers().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<IndexSettings> getIndexSettings(final String index) {
    try {
      final var request = new Request("GET", index + "/_settings");
      final var response = restClient.performRequest(request);
      final TypeReference<Map<String, IndexSettings>> mapTypeReference = new TypeReference<>() {};
      final Map<String, IndexSettings> settings =
          MAPPER.readValue(response.getEntity().getContent(), mapTypeReference);
      return settings.values().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndices() {
    try {
      final var request = new Request("DELETE", config.index.prefix + "*");
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndexTemplates() {
    try {
      final var request =
          new Request("DELETE", "/_index_template/%s*".formatted(config.index.prefix));
      restClient.performRequest(request);
    } catch (final IOException e) {
      if (e.getMessage() != null && e.getMessage().contains("404 Not Found")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new UncheckedIOException(e);
    }
  }

  void deleteComponentTemplates() {
    try {
      final var request =
          new Request("DELETE", "/_component_template/%s*".formatted(config.index.prefix));
      restClient.performRequest(request);
    } catch (final IOException e) {
      if (e.getMessage() != null && e.getMessage().contains("404 Not Found")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new UncheckedIOException(e);
    }
  }

  ElasticsearchClient getEsClient() {
    return esClient;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(esClient._transport());
  }

  record IndexTemplatesDto(@JsonProperty("index_templates") List<IndexTemplateWrapper> wrappers) {
    record IndexTemplateWrapper(String name, @JsonProperty("index_template") Template template) {}
  }

  record ComponentTemplatesDto(
      @JsonProperty("component_templates") List<ComponentTemplateWrapper> wrappers) {
    record ComponentTemplateWrapper(
        String name, @JsonProperty("component_template") Template template) {}
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record IndexSettings(Settings settings) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Settings(Index index) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Index(Lifecycle lifecycle) {}

    record Lifecycle(String name) {}
  }
}
