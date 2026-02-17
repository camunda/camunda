/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.VersionUtil;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettings.Builder;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;

/** Utility class to read index and component templates from the resources in a logical format. */
@SuppressWarnings("ClassCanBeRecord") // not semantically a data class
final class TemplateReader {
  @SuppressWarnings("java:S1075") // not an actual URI
  private static final String INDEX_TEMPLATE_FILENAME_PATTERN = "/zeebe-record-%s-template.json";

  private static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final OpensearchExporterConfiguration.IndexConfiguration config;

  public TemplateReader(final IndexConfiguration config) {
    this.config = config;
  }

  /**
   * Reads the index template for the given value type from the resources and builds a
   * PutIndexTemplateRequest with configured shards and replicas, and given alias and search
   * patterns.
   */
  PutIndexTemplateRequest getPutIndexTemplateRequest(
      final String templateName,
      final ValueType valueType,
      final String searchPattern,
      final String aliasName) {
    try {
      final Template wrapper = readIndexTemplate(valueType);

      return PutIndexTemplateRequest.of(
          b ->
              b.name(templateName)
                  .version(wrapper.version())
                  .priority(config.getPriority())
                  .composedOf(config.prefix + "-" + VersionUtil.getVersionLowerCase())
                  .indexPatterns(searchPattern)
                  .template(
                      t ->
                          t.settings(updateSettings(wrapper.template().settings()))
                              .aliases(aliasName, Alias.builder().build())
                              .mappings(wrapper.template().mappings())));
    } catch (final Exception e) {
      throw new OpensearchExporterException(
          "Failed to create a put index template request from classpath "
              + findResourceForTemplate(valueType),
          e);
    }
  }

  /**
   * Reads the component template from resources and builds a PutComponentTemplateRequest with
   * configured shards and replicas.
   */
  PutComponentTemplateRequest getComponentTemplatePutRequest(final String name) {
    try {
      final Template wrapper = getTemplateWrapperFromClasspath(ZEEBE_RECORD_TEMPLATE_JSON);

      return PutComponentTemplateRequest.of(
          b ->
              b.name(name)
                  .version(wrapper.version())
                  .template(
                      t ->
                          t.settings(updateSettings(wrapper.template().settings()))
                              .mappings(wrapper.template().mappings())));
    } catch (final Exception e) {
      throw new OpensearchExporterException(
          "Failed to create a put component template request from classpath "
              + ZEEBE_RECORD_TEMPLATE_JSON,
          e);
    }
  }

  Template readComponentTemplate() {
    return getTemplateWrapperFromClasspath(ZEEBE_RECORD_TEMPLATE_JSON);
  }

  Template readIndexTemplate(final ValueType valueType) {
    return getTemplateWrapperFromClasspath(findResourceForTemplate(valueType));
  }

  private Template getTemplateWrapperFromClasspath(final String filename) {
    try (final InputStream inputStream = OpensearchExporter.class.getResourceAsStream(filename)) {
      if (inputStream == null) {
        throw new OpensearchExporterException(
            "Template resource not found on classpath " + filename);
      }
      final JsonbJsonpMapper mapper = new JsonbJsonpMapper();
      final JsonParser parser = mapper.jsonProvider().createParser(inputStream);
      return Template.DESERIALIZER.deserialize(parser, mapper);
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load template from classpath " + filename, e);
    }
  }

  private IndexSettings updateSettings(final IndexSettings settings) {
    if (settings == null) {
      return null;
    }

    final Builder settingsBuilder = settings.toBuilder();
    if (settings.index() != null) {
      final IndexSettings idxSettings = updateIndexSettings(settings.index().toBuilder()).build();
      settingsBuilder.index(idxSettings);
    }
    return settingsBuilder.build();
  }

  private Builder updateIndexSettings(final Builder builder) {
    final Integer numberOfShards = config.getNumberOfShards();
    if (numberOfShards != null) {
      builder.numberOfShards(numberOfShards);
    }
    final Integer numberOfReplicas = config.getNumberOfReplicas();
    if (numberOfReplicas != null) {
      builder.numberOfReplicas(numberOfReplicas);
    }
    return builder;
  }

  private String findResourceForTemplate(final ValueType valueType) {
    return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
  }

  private String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replace("_", "-");
  }
}
