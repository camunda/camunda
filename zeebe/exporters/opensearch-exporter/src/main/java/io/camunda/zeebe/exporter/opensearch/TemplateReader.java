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
    return Template.MutableCopyBuilder.copyOf(template)
        .updateComposedOf(
            composedOf ->
                composedOf.set(0, config.prefix + "-" + VersionUtil.getVersionLowerCase()))
        .updatePatterns(patterns -> patterns.set(0, searchPattern))
        .updateAliases(
            aliases -> {
              aliases.clear();
              aliases.put(aliasName, Collections.emptyMap());
            })
        .withPriority(Long.valueOf(config.getPriority()))
        .build();
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
    } catch (final Exception e) {
      throw new OpensearchExporterException(
          "Failed to load index template from classpath " + filename, e);
    }
  }
}
