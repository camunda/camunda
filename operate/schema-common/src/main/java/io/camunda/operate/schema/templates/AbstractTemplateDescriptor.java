/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.templates;

import static io.camunda.operate.schema.indices.AbstractIndexDescriptor.SCHEMA_FOLDER_ELASTICSEARCH;
import static io.camunda.operate.schema.indices.AbstractIndexDescriptor.SCHEMA_FOLDER_OPENSEARCH;

import io.camunda.operate.conditions.DatabaseInfoProvider;

public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  private static final String SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/template/operate-%s.json";
  private static final String SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/template/operate-%s.json";

  private final String indexPrefix;
  private final DatabaseInfoProvider databaseInfoProvider;

  public AbstractTemplateDescriptor(
      final String indexPrefix, final DatabaseInfoProvider databaseInfoProvider) {
    this.indexPrefix = indexPrefix;
    this.databaseInfoProvider = databaseInfoProvider;
  }

  @Override
  public String getFullQualifiedName() {
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return String.format("%s-%s-\\d.*", indexPrefix, getIndexName());
  }

  @Override
  public String getSchemaClasspathFilename() {
    if (databaseInfoProvider.isElasticsearch()) {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH, getIndexName());
    }
  }
}
