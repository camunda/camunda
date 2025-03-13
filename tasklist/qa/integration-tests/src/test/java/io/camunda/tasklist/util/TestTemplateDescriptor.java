/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.schema.templates.AbstractTemplateDescriptor;

public class TestTemplateDescriptor extends AbstractTemplateDescriptor {

  private final String indexName;
  private String schemaClasspathFilename;

  public TestTemplateDescriptor(final String indexName, final String schemaClasspathFilename) {
    this.indexName = indexName;
    this.schemaClasspathFilename = schemaClasspathFilename;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getSchemaClasspathFilename() {
    return schemaClasspathFilename;
  }

  public void setSchemaClasspathFilename(final String schemaClasspathFilename) {
    this.schemaClasspathFilename = schemaClasspathFilename;
  }
}
