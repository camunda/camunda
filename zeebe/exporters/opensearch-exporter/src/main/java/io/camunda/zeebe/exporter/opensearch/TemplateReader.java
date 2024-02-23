/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.exporter.opensearch.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

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

  /** Reads the shared component template from the resources. */
  Template readComponentTemplate() {
    return readTemplate(ZEEBE_RECORD_TEMPLATE_JSON);
  }

  /**
   * Reads the index template for the given value type from the resources, and replaces the alias
   * and search patterns with the given ones. Additionally, will update the composed_of to match the
   * configured index prefix.
   */
  Template readIndexTemplate(
      final ValueType valueType, final String searchPattern, final String aliasName) {
    final Template template = readTemplate(findResourceForTemplate(valueType));

    // update prefix in template in case it was changed in configuration
    template.composedOf().set(0, config.prefix);

    template.patterns().set(0, searchPattern);
    template.template().aliases().clear();
    template.template().aliases().put(aliasName, Collections.emptyMap());

    return template;
  }

  private String findResourceForTemplate(final ValueType valueType) {
    return String.format(INDEX_TEMPLATE_FILENAME_PATTERN, valueTypeToString(valueType));
  }

  private String valueTypeToString(final ValueType valueType) {
    return valueType.name().toLowerCase().replace("_", "-");
  }

  private Template readTemplate(final String resourcePath) {
    final Template template = getTemplateFromClasspath(resourcePath);
    final Map<String, Object> settings = template.template().settings();

    substituteConfiguration(settings);

    return template;
  }

  private void substituteConfiguration(final Map<String, Object> settings) {
    // update number of shards in template in case it was changed in configuration
    final Integer numberOfShards = config.getNumberOfShards();
    if (numberOfShards != null) {
      settings.put("number_of_shards", numberOfShards);
    }

    // update number of replicas in template in case it was changed in configuration
    final Integer numberOfReplicas = config.getNumberOfReplicas();
    if (numberOfReplicas != null) {
      settings.put("number_of_replicas", numberOfReplicas);
    }
  }

  private Template getTemplateFromClasspath(final String filename) {
    try (final InputStream inputStream = OpensearchExporter.class.getResourceAsStream(filename)) {
      return MAPPER.readValue(inputStream, Template.class);
    } catch (final IOException e) {
      throw new OpensearchExporterException(
          "Failed to load index template from classpath " + filename, e);
    }
  }
}
