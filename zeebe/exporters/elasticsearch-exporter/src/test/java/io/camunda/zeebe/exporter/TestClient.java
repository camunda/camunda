/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ComponentTemplate;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * A thin client to verify properties from Elastic. Wraps the high-level ES Java client in a
 * closeable resource.
 */
final class TestClient implements CloseableSilently {

  private final ElasticsearchExporterConfiguration config;
  private final ElasticsearchClient esClient;
  private final RecordIndexRouter indexRouter;

  TestClient(final ElasticsearchExporterConfiguration config, final RecordIndexRouter indexRouter) {
    this.config = config;
    this.indexRouter = indexRouter;
    esClient = ElasticsearchClientFactory.of(config);
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

  Optional<IndexTemplateItem> getIndexTemplate(final ValueType valueType, final String version) {
    try {
      final var templateName = indexRouter.indexPrefixForValueType(valueType, version);
      final var response = esClient.indices().getIndexTemplate(b -> b.name(templateName));
      return response.indexTemplates().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<ComponentTemplate> getComponentTemplate() {
    try {
      final var templateName = config.index.prefix + "-" + VersionUtil.getVersionLowerCase();
      final var response = esClient.cluster().getComponentTemplate(b -> b.name(templateName));
      return response.componentTemplates().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Optional<IndexState> getIndexSettings(final String index) {
    try {
      final var response = esClient.indices().getSettings(b -> b.index(index));
      return response.result().values().stream().findFirst();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndices() {
    try {
      esClient.indices().delete(b -> b.index(config.index.prefix + "*"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void deleteIndexTemplates() {
    try {
      esClient.indices().deleteIndexTemplate(b -> b.name(config.index.prefix + "*"));
    } catch (final Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("404")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new RuntimeException(e);
    }
  }

  void deleteComponentTemplates() {
    try {
      esClient.cluster().deleteComponentTemplate(b -> b.name(config.index.prefix + "*"));
    } catch (final Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("404")) {
        // Ignore 404 errors - no templates to delete
        return;
      }
      throw new RuntimeException(e);
    }
  }

  ElasticsearchClient getEsClient() {
    return esClient;
  }

  @Override
  public void close() {
    try {
      esClient._transport().close();
    } catch (final IOException e) {
      // ignore
    }
  }
}
