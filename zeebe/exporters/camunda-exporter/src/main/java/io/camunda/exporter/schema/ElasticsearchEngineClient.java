/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexTemplateSummary;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import co.elastic.clients.json.JsonpDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ElasticsearchProperties.IndexSettings;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchEngineClient implements SearchEngineClient {
  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEngineClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final ElasticsearchClient client;

  public ElasticsearchEngineClient(final ElasticsearchClient client) {
    this.client = client;
  }

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor) {
    final CreateIndexRequest request = createIndexRequest(indexDescriptor);
    try {
      client.indices().create(request);
      LOG.debug("Index [{}] was successfully created", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getIndexName());
      LOG.error(errMsg, e);
      throw new ElasticsearchExporterException(errMsg, e);
    }
  }

  @Override
  public void createIndexTemplate(
      final IndexTemplateDescriptor templateDescriptor,
      final IndexSettings settings,
      final boolean create) {
    final PutIndexTemplateRequest request =
        putIndexTemplateRequest(templateDescriptor, settings, create);

    try {
      client.indices().putIndexTemplate(request);
      LOG.debug("Template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Template [%s] was NOT created", templateDescriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new ElasticsearchExporterException(errMsg, e);
    }
  }

  @Override
  public void putMapping(
      final IndexDescriptor indexDescriptor, final Set<IndexMappingProperty> newProperties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, newProperties);

    try {
      client.indices().putMapping(request);
      LOG.debug("Mapping in [{}] was successfully updated", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Mapping in [%s] was NOT updated", indexDescriptor.getIndexName());
      LOG.error(errMsg, e);
      throw new ElasticsearchExporterException(errMsg, e);
    }
  }

  @Override
  public Map<String, IndexMapping> getMappings(
      final String namePattern, final MappingSource mappingSource) {
    try {
      final Map<String, TypeMapping> mappings = getCurrentMappings(mappingSource, namePattern);

      return mappings.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Entry::getKey,
                  entry -> {
                    final var mappingsBlock = entry.getValue();
                    return new IndexMapping.Builder()
                        .indexName(entry.getKey())
                        .dynamic(dynamicFromMappings(mappingsBlock))
                        .properties(propertiesFromMappings(mappingsBlock))
                        .metaProperties(metaFromMappings(mappingsBlock))
                        .build();
                  }));
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          String.format(
              "Failed retrieving mappings from index/index templates with pattern [%s]",
              namePattern),
          e);
    }
  }

  private Map<String, TypeMapping> getCurrentMappings(
      final MappingSource mappingSource, final String namePattern) throws IOException {
    if (mappingSource == MappingSource.INDEX) {
      return client.indices().getMapping(req -> req.index(namePattern)).result().entrySet().stream()
          .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().mappings()));
    } else if (mappingSource == MappingSource.INDEX_TEMPLATE) {
      return client
          .indices()
          .getIndexTemplate(req -> req.name(namePattern))
          .indexTemplates()
          .stream()
          .filter(indexTemplateItem -> indexTemplateItem.indexTemplate().template() != null)
          .collect(
              Collectors.toMap(
                  IndexTemplateItem::name, item -> item.indexTemplate().template().mappings()));
    } else {
      throw new IndexSchemaValidationException(
          "Invalid mapping source provided must be either INDEX or INDEX_TEMPLATE");
    }
  }

  private Set<IndexMappingProperty> propertiesFromMappings(final TypeMapping mapping) {
    return mapping.properties().entrySet().stream()
        .map(
            p ->
                new IndexMappingProperty.Builder()
                    .name(p.getKey())
                    .typeDefinition(Map.of("type", p.getValue()._kind().jsonValue()))
                    .build())
        .collect(Collectors.toSet());
  }

  private String dynamicFromMappings(final TypeMapping mapping) {
    final var dynamic = mapping.dynamic();
    return dynamic == null ? "strict" : dynamic.toString().toLowerCase();
  }

  private Map<String, Object> metaFromMappings(final TypeMapping mapping) {
    return mapping.meta().entrySet().stream()
        .collect(Collectors.toMap(Entry::getKey, ent -> ent.getValue().to(Object.class)));
  }

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final Set<IndexMappingProperty> newProperties) {

    return new PutMappingRequest.Builder()
        .index(indexDescriptor.getIndexName())
        .withJson(IndexMappingProperty.toPropertiesJson(newProperties, MAPPER))
        .build();
  }

  public <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    try (final var parser = client._jsonpMapper().jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, client._jsonpMapper());
    }
  }

  private InputStream getResourceAsStream(final String classpathFileName) {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathFileName);
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor,
      final IndexSettings settings,
      final Boolean create) {

    try (final var templateMappings =
        getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {

      return new PutIndexTemplateRequest.Builder()
          .name(indexTemplateDescriptor.getTemplateName())
          .indexPatterns(indexTemplateDescriptor.getIndexPattern())
          .template(
              t ->
                  t.aliases(indexTemplateDescriptor.getAlias(), Alias.of(a -> a))
                      .mappings(
                          deserializeJson(IndexTemplateSummary._DESERIALIZER, templateMappings)
                              .mappings())
                      .settings(
                          s ->
                              s.index(
                                  i ->
                                      i.numberOfShards(String.valueOf(settings.getNumberOfShards()))
                                          .numberOfReplicas(
                                              String.valueOf(settings.getNumberOfReplicas())))))
          .composedOf(indexTemplateDescriptor.getComposedOf())
          .create(create)
          .build();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private CreateIndexRequest createIndexRequest(final IndexDescriptor indexDescriptor) {
    try (final var templateMappings =
        getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {

      return new CreateIndexRequest.Builder()
          .index(indexDescriptor.getFullQualifiedName())
          .aliases(indexDescriptor.getAlias(), a -> a.isWriteIndex(false))
          .mappings(
              deserializeJson(IndexTemplateSummary._DESERIALIZER, templateMappings).mappings())
          .build();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load file "
              + indexDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  public enum MappingSource {
    INDEX,
    INDEX_TEMPLATE
  }
}
