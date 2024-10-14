/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static io.camunda.exporter.utils.SearchEngineClientUtils.appendToFileSchemaSettings;

import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.OpensearchExporterException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpensearchEngineClient implements SearchEngineClient {
  private static final Logger LOG = LoggerFactory.getLogger(OpensearchEngineClient.class);
  private final OpenSearchClient client;

  public OpensearchEngineClient(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor, final IndexSettings settings) {
    final CreateIndexRequest request = createIndexRequest(indexDescriptor, settings);

    try {
      client.indices().create(request);
      LOG.debug("Index [{}] was successfully created", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Index [%s] was not created", indexDescriptor.getIndexName());
      LOG.error(errMsg, e);
      throw new OpensearchExporterException(errMsg, e);
    }
  }

  @Override
  public void createIndexTemplate(
      final IndexTemplateDescriptor templateDescriptor,
      final IndexSettings settings,
      final boolean create) {

    final PutIndexTemplateRequest request = putIndexTemplateRequest(templateDescriptor, settings);

    try {
      if (create
          && client
              .indices()
              .existsIndexTemplate(req -> req.name(templateDescriptor.getTemplateName()))
              .value()) {
        throw new OpensearchExporterException(
            String.format(
                "Cannot update template [%s] as create = true",
                templateDescriptor.getTemplateName()));
      }

      client.indices().putIndexTemplate(request);
      LOG.debug("Template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Template [%s] was NOT created", templateDescriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new OpensearchExporterException(errMsg, e);
    }
  }

  @Override
  public void putMapping(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, newProperties);

    try {
      client.indices().putMapping(request);
      LOG.debug("Mapping in [{}] was successfully updated", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Mapping in [%s] was NOT updated", indexDescriptor.getIndexName());
      LOG.error(errMsg, e);
      throw new OpensearchExporterException(errMsg, e);
    }
  }

  @Override
  public Map<String, IndexMapping> getMappings(
      final String namePattern, final MappingSource mappingSource) {
    return Map.of();
  }

  @Override
  public void putSettings(
      final List<IndexDescriptor> indexDescriptors, final Map<String, String> toAppendSettings) {}

  @Override
  public void putIndexLifeCyclePolicy(final String policyName, final String deletionMinAge) {}

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final Collection<IndexMappingProperty> newProperties) {

    final var opensearchProperties =
        newProperties.stream()
            .map(IndexMappingProperty::toOpensearchProperty)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    return new PutMappingRequest.Builder()
        .index(indexDescriptor.getFullQualifiedName())
        .properties(opensearchProperties)
        .build();
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor, final IndexSettings settings) {

    try (final var templateFile =
        getClass().getResourceAsStream(indexTemplateDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              appendToFileSchemaSettings(templateFile, settings));

      return new PutIndexTemplateRequest.Builder()
          .name(indexTemplateDescriptor.getTemplateName())
          .indexPatterns(indexTemplateDescriptor.getIndexPattern())
          .template(
              t ->
                  t.aliases(indexTemplateDescriptor.getAlias(), a -> a)
                      .mappings(templateFields.mappings())
                      .settings(templateFields.settings()))
          .composedOf(indexTemplateDescriptor.getComposedOf())
          .build();
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private CreateIndexRequest createIndexRequest(
      final IndexDescriptor indexDescriptor, final IndexSettings settings) {

    try (final var templateFile =
        getClass().getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {

      final var templateFields =
          deserializeJson(
              IndexTemplateMapping._DESERIALIZER,
              appendToFileSchemaSettings(templateFile, settings));

      return new CreateIndexRequest.Builder()
          .index(indexDescriptor.getFullQualifiedName())
          .aliases(indexDescriptor.getAlias(), a -> a.isWriteIndex(false))
          .mappings(templateFields.mappings())
          .settings(templateFields.settings())
          .build();

    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load file: "
              + indexDescriptor.getMappingsClasspathFilename()
              + " from classpath",
          e);
    }
  }

  private <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    final JsonbJsonpMapper mapper = new JsonbJsonpMapper();

    try (final var parser = mapper.jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, mapper);
    }
  }
}
