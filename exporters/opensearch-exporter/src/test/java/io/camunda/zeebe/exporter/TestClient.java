/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.transport.rest_client.RestClientTransport;

/**
 * A thin client to verify properties from Opensearch. Wraps both the low and high level clients
 * from Opensearch in a closeable resource.
 */
final class TestClient implements CloseableSilently {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  private final OpensearchExporterConfiguration config;
  private final RestClient restClient;
  private final OpenSearchClient osClient;
  private final RecordIndexRouter indexRouter;

  TestClient(final OpensearchExporterConfiguration config, final RecordIndexRouter indexRouter) {
    this.config = config;
    this.indexRouter = indexRouter;

    restClient = RestClientFactory.of(config, true);

    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(MAPPER));
    osClient = new OpenSearchClient(transport);
  }

  @SuppressWarnings("rawtypes")
  GetResponse<Record> getExportedDocumentFor(final Record<?> record) {
    final var indexName = indexRouter.indexFor(record);

    try {
      osClient.indices().refresh(b -> b.index(indexName)); // ensure latest data is visible
      return osClient.get(b -> b.id(indexRouter.idFor(record)).index(indexName), Record.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<IndexTemplateWrapper> getIndexTemplate(final ValueType valueType) {
    try {
      final var request =
          new Request("GET", "/_index_template/" + indexRouter.indexPrefixForValueType(valueType));
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
      final var request = new Request("GET", "/_component_template/" + config.index.prefix);
      final var response = restClient.performRequest(request);
      final var templates =
          MAPPER.readValue(response.getEntity().getContent(), ComponentTemplatesDto.class);
      return templates.wrappers().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void putUser(final String username, final String password, final List<String> roles) {
    try {
      final var request = new Request("PUT", "/_plugins/_security/api/internalusers/" + username);
      final var putUserRequest = new PutUserRequest(password, roles);
      request.setJsonEntity(MAPPER.writeValueAsString(putUserRequest));
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  OpenSearchClient getOsClient() {
    return osClient;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(osClient._transport());
  }

  record IndexTemplatesDto(@JsonProperty("index_templates") List<IndexTemplateWrapper> wrappers) {
    record IndexTemplateWrapper(String name, @JsonProperty("index_template") Template template) {}
  }

  record ComponentTemplatesDto(
      @JsonProperty("component_templates") List<ComponentTemplateWrapper> wrappers) {
    record ComponentTemplateWrapper(
        String name, @JsonProperty("component_template") Template template) {}
  }

  @JsonInclude(Include.NON_EMPTY)
  private record PutUserRequest(
      String password, @JsonProperty("backend_roles") List<String> roles) {}
}
