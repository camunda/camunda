/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.dto.Template;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/** Utility class to read index and component templates from the resources in a logical format. */
@SuppressWarnings("ClassCanBeRecord") // not semantically a data class
public final class TemplateReader {
  @SuppressWarnings("java:S1075") // not an actual URI
  private static final String INDEX_TEMPLATE_FILENAME_PATTERN = "/zeebe-record-%s-template.json";

  private static final String ZEEBE_RECORD_TEMPLATE_JSON = "/zeebe-record-template.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Integer numberOfShards;
  private Integer numberOfReplicas;
  private String retentionPolicyName;
  private String indexPrefix;

  public TemplateReader(final ElasticsearchExporterConfiguration config) {
    this(
        config.index.getNumberOfShards(),
        config.index.getNumberOfReplicas(),
        config.retention.isEnabled() ? config.retention.getPolicyName() : null,
        config.index.prefix);
  }

  public TemplateReader(String indexPrefix) {
    this(null, null, null, indexPrefix);
  }

  public TemplateReader(
      Integer numberOfShards,
      Integer numberOfReplicas,
      String retentionPolicyName,
      String indexPrefix) {
    this.numberOfReplicas = numberOfReplicas;
    this.numberOfShards = numberOfShards;
    this.retentionPolicyName = retentionPolicyName;
    this.indexPrefix = indexPrefix;
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
    template.composedOf().set(0, indexPrefix);

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
    if (numberOfShards != null) {
      settings.put("number_of_shards", numberOfShards);
    }

    // update number of replicas in template in case it was changed in configuration
    if (numberOfReplicas != null) {
      settings.put("number_of_replicas", numberOfReplicas);
    }

    // update index.lifecycle in template in case a retention policy is configured
    if (retentionPolicyName != null) {
      settings.put("index.lifecycle.name", retentionPolicyName);
    }
  }

  private Template getTemplateFromClasspath(final String filename) {
    try (final InputStream inputStream =
        ElasticsearchExporter.class.getResourceAsStream(filename)) {
      return MAPPER.readValue(inputStream, Template.class);
    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          "Failed to load index template from classpath " + filename, e);
    }
  }
}
