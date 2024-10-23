/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  public static final String SCHEMA_FOLDER_OPENSEARCH = "/schema/opensearch/create";
  public static final String SCHEMA_FOLDER_ELASTICSEARCH = "/schema/elasticsearch/create";
  public static final String SCHEMA_CREATE_INDEX_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/index/%s-%s.json";
  public static final String SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/index/%s-%s.json";

  protected String indexPrefix;
  protected boolean isElasticsearch;

  // Will not use global prefix unless this value is true
  protected boolean hasGlobalPrefix = false;

  public AbstractIndexDescriptor(final String indexPrefix, final boolean isElasticsearch) {
    this.indexPrefix = indexPrefix;
    this.isElasticsearch = isElasticsearch;
  }

  @Override
  public String getFullQualifiedName() {
    if (hasGlobalPrefix) {
      return String.format(
          "%s%s-%s-%s_", formattedIndexPrefix(), getComponentName(), getIndexName(), getVersion());
    } else {
      return String.format("%s%s-%s_", formattedIndexPrefix(), getIndexName(), getVersion());
    }
  }

  @Override
  public String getAlias() {
    return getFullQualifiedName() + "alias";
  }

  @Override
  public String getMappingsClasspathFilename() {
    return isElasticsearch
        ? String.format(SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH, getComponentName(), getIndexName())
        : String.format(SCHEMA_CREATE_INDEX_JSON_OPENSEARCH, getComponentName(), getIndexName());
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    if (hasGlobalPrefix) {
      return String.format(
          "%s%s-%s-\\d.*", formattedIndexPrefix(), getComponentName(), getIndexName());
    } else {
      return String.format("%s%s-\\d.*", formattedIndexPrefix(), getIndexName());
    }
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  private String formattedIndexPrefix() {
    return getIndexPrefix() != null && !getIndexPrefix().isBlank() ? getIndexPrefix() + "-" : "";
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public AbstractIndexDescriptor withHasGlobalPrefix(final boolean isGlobalPrefix) {
    hasGlobalPrefix = isGlobalPrefix;
    return this;
  }

  public abstract String getComponentName();
}
