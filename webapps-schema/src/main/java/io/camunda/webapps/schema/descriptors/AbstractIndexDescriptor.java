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
  public static final String FULL_QUALIFIED_INDEX_NAME_PATTERN = "%s%s-%s-%s_";
  public static final String INDEX_NAME_WITHOUT_VERSION_PATTERN = "%s%s-%s";
  public static final String ALL_VERSIONS_INDEX_NAME_PATTERN = "%s%s-%s-\\d.*";

  protected String indexPrefix;
  protected boolean isElasticsearch;

  public AbstractIndexDescriptor(final String indexPrefix, final boolean isElasticsearch) {
    this.indexPrefix = indexPrefix;
    this.isElasticsearch = isElasticsearch;
  }

  @Override
  public String getFullQualifiedName() {
    return String.format(
        FULL_QUALIFIED_INDEX_NAME_PATTERN,
        formattedIndexPrefix(),
        getComponentName(),
        getIndexName(),
        getVersion());
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
    return String.format(
        ALL_VERSIONS_INDEX_NAME_PATTERN,
        formattedIndexPrefix(),
        getComponentName(),
        getIndexName());
  }

  @Override
  public String getIndexNameWithoutVersion() {
    return String.format(
        INDEX_NAME_WITHOUT_VERSION_PATTERN,
        formattedIndexPrefix(),
        getComponentName(),
        getIndexName());
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  private String formattedIndexPrefix() {
    return formatIndexPrefix(getIndexPrefix());
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public abstract String getComponentName();

  public static String formatIndexPrefix(final String indexPrefix) {
    return indexPrefix != null && !indexPrefix.isBlank() ? indexPrefix + "-" : "";
  }
}
