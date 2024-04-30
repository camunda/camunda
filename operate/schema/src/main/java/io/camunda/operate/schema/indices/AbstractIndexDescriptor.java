/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("databaseInfo")
public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  public static final String SCHEMA_FOLDER_OPENSEARCH = "/schema/opensearch/create";
  public static final String SCHEMA_FOLDER_ELASTICSEARCH = "/schema/elasticsearch/create";
  private static final String SCHEMA_CREATE_INDEX_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/index/operate-%s.json";
  private static final String SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/index/operate-%s.json";

  @Autowired protected OperateProperties operateProperties;

  @Override
  public String getFullQualifiedName() {
    return String.format(
        "%s-%s-%s_", operateProperties.getIndexPrefix(), getIndexName(), getVersion());
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return String.format("%s-%s-\\d.*", operateProperties.getIndexPrefix(), getIndexName());
  }

  @Override
  public String getSchemaClasspathFilename() {
    if (DatabaseInfo.isElasticsearch()) {
      return String.format(SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_INDEX_JSON_OPENSEARCH, getIndexName());
    }
  }
}
