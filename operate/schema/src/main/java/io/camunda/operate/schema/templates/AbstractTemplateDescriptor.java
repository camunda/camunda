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

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractTemplateDescriptor implements TemplateDescriptor {

  private static final String SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/template/operate-%s.json";
  private static final String SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/template/operate-%s.json";

  @Autowired private OperateProperties operateProperties;

  @Override
  public String getFullQualifiedName() {
    final var indexPrefix =
        DatabaseInfo.isOpensearch()
            ? operateProperties.getOpensearch().getIndexPrefix()
            : operateProperties.getElasticsearch().getIndexPrefix();
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
  }

  @Override
  public String getSchemaClasspathFilename() {
    if (DatabaseInfo.isElasticsearch()) {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_TEMPLATE_JSON_OPENSEARCH, getIndexName());
    }
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return String.format("%s-%s-\\d.*", operateProperties.getIndexPrefix(), getIndexName());
  }
}
