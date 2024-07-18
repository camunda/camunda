/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.indices;

import static io.camunda.tasklist.property.TasklistProperties.ELASTIC_SEARCH;

import io.camunda.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractIndexDescriptor implements IndexDescriptor {
  public static final String PARTITION_ID = "partitionId";

  public static final String SCHEMA_FOLDER_OPENSEARCH = "/schema/os/create";
  public static final String SCHEMA_FOLDER_ELASTICSEARCH = "/schema/es/create";

  private static final String SCHEMA_CREATE_INDEX_JSON_OPENSEARCH =
      SCHEMA_FOLDER_OPENSEARCH + "/index/tasklist-%s.json";
  private static final String SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH =
      SCHEMA_FOLDER_ELASTICSEARCH + "/index/tasklist-%s.json";

  @Autowired protected TasklistProperties tasklistProperties;

  @Override
  public String getFullQualifiedName() {
    final String indexPrefix =
        ELASTIC_SEARCH.equals(tasklistProperties.getDatabase())
            ? tasklistProperties.getElasticsearch().getIndexPrefix()
            : tasklistProperties.getOpenSearch().getIndexPrefix();
    return String.format("%s-%s-%s_", indexPrefix, getIndexName(), getVersion());
  }

  @Override
  public String getSchemaClasspathFilename() {
    if (ELASTIC_SEARCH.equals(tasklistProperties.getDatabase())) {
      return String.format(SCHEMA_CREATE_INDEX_JSON_ELASTICSEARCH, getIndexName());
    } else {
      return String.format(SCHEMA_CREATE_INDEX_JSON_OPENSEARCH, getIndexName());
    }
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return String.format("%s-%s-\\d.*", tasklistProperties.getIndexPrefix(), getIndexName());
  }
}
