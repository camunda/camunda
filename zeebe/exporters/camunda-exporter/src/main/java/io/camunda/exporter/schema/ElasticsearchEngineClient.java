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
import co.elastic.clients.elasticsearch.cluster.PutComponentTemplateRequest;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexTemplateSummary;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.json.JsonpDeserializer;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.exporter.schema.descriptors.ComponentTemplateDescriptor;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchEngineClient implements SearchEngineClient {
  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEngineClient.class);
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
  public void createIndexTemplate(final IndexTemplateDescriptor templateDescriptor) {
    final PutIndexTemplateRequest request = putIndexTemplateRequest(templateDescriptor);

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
  public void createComponentTemplate(final ComponentTemplateDescriptor templateDescriptor) {
    final PutComponentTemplateRequest request = putComponentTemplateRequest(templateDescriptor);

    try {
      client.cluster().putComponentTemplate(request);
      LOG.debug(
          "Component template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException e) {
      final var errMsg =
          String.format(
              "Component template [%s] was NOT created", templateDescriptor.getTemplateName());
      LOG.error(errMsg, e);
      throw new ElasticsearchExporterException(errMsg, e);
    }
  }

  @Override
  public void putMapping(final IndexDescriptor indexDescriptor, final String properties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, properties);

    try {
      client.indices().putMapping(request);
      LOG.debug("Mapping in [{}] was successfully updated", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      final var errMsg =
          String.format("Mapping in [{}] was NOT updated", indexDescriptor.getIndexName());
      LOG.error(errMsg, e);
      throw new ElasticsearchExporterException(errMsg, e);
    }
  }

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final String properties) {

    try (final var propertiesStream = IOUtils.toInputStream(properties, StandardCharsets.UTF_8)) {
      return new PutMappingRequest.Builder()
          .index(indexDescriptor.getIndexName())
          .properties(deserializeJson(TypeMapping._DESERIALIZER, propertiesStream).properties())
          .build();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load properties json into stream for put mapping into indexes matching the descriptor: ["
              + indexDescriptor
              + "]",
          e);
    }
  }

  private PutComponentTemplateRequest putComponentTemplateRequest(
      final ComponentTemplateDescriptor templateDescriptor) {
    try (final var template =
        getResourceAsStream(templateDescriptor.getTemplateClasspathFileName())) {
      return new PutComponentTemplateRequest.Builder()
          .name(templateDescriptor.getTemplateName())
          .withJson(template)
          .create(templateDescriptor.create())
          .build();
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load json into stream for component template: ["
              + templateDescriptor.getTemplateName()
              + "]",
          e);
    }
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
      final IndexTemplateDescriptor indexTemplateDescriptor) {

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
                              .mappings()))
          .composedOf(indexTemplateDescriptor.getComposedOf())
          .create(true)
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
}
