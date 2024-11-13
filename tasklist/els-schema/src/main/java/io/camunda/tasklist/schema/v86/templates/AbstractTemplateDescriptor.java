/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.templates;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;
import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.SCHEMA_FOLDER_ELASTICSEARCH;
import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.SCHEMA_FOLDER_OPENSEARCH;
import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.formatAllVersionsIndexNameRegexPattern;
import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.formatFullQualifiedIndexName;

import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  private static final String SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/template/tasklist-%s.json";
  private static final String SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/template/tasklist-%s.json";

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public String getFullQualifiedName() {
    return formatFullQualifiedIndexName(getIndexPrefix(), getIndexName(), getVersion());
  }

  @Override
  public String getAlias() {
    return getFullQualifiedName() + "alias";
  }

  @Override
  public String getMappingsClasspathFilename() {
    if (ELASTIC_SEARCH.equals(tasklistProperties.getDatabase())) {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH, getIndexName());
    }
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return formatAllVersionsIndexNameRegexPattern(getIndexPrefix(), getIndexName());
  }

  private String getIndexPrefix() {
    return ELASTIC_SEARCH.equals(tasklistProperties.getDatabase())
        ? tasklistProperties.getElasticsearch().getIndexPrefix()
        : tasklistProperties.getOpenSearch().getIndexPrefix();
  }
}
