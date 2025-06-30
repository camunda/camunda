/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.CONFIG_PREFIX;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;

public class TestTemplateDescriptor extends AbstractTemplateDescriptor {

  private String mappingsClasspathFilename;

  private final String indexName;

  public TestTemplateDescriptor(
      final String indexPrefix,
      final boolean isElasticsearch,
      final String indexName,
      final String mappingsClasspathFilename) {
    super(indexPrefix, isElasticsearch);
    this.indexName = indexName;
    this.mappingsClasspathFilename = mappingsClasspathFilename;
  }

  public TestTemplateDescriptor(final String indexName, final String mappingsClasspathFilename) {
    this(CONFIG_PREFIX, true, indexName, mappingsClasspathFilename);
  }

  @Override
  public String getComponentName() {
    return "test";
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getMappingsClasspathFilename() {
    return mappingsClasspathFilename;
  }

  public void setMappingsClasspathFilename(final String mappingsClasspathFilename) {
    this.mappingsClasspathFilename = mappingsClasspathFilename;
  }
}
