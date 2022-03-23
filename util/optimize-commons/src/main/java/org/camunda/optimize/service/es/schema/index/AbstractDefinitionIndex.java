/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class AbstractDefinitionIndex extends DefaultIndexMappingCreator {
  public static final String DEFINITION_ID = DefinitionOptimizeResponseDto.Fields.id;
  public static final String DEFINITION_KEY = DefinitionOptimizeResponseDto.Fields.key;
  public static final String DEFINITION_VERSION = DefinitionOptimizeResponseDto.Fields.version;
  public static final String DEFINITION_VERSION_TAG = DefinitionOptimizeResponseDto.Fields.versionTag;
  public static final String DEFINITION_NAME = DefinitionOptimizeResponseDto.Fields.name;
  public static final String DATA_SOURCE = DefinitionOptimizeResponseDto.Fields.dataSource;
  public static final String DEFINITION_TENANT_ID = DefinitionOptimizeResponseDto.Fields.tenantId;
  public static final String DEFINITION_DELETED = DefinitionOptimizeResponseDto.Fields.deleted;

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_VERSION_TAG)
        .field("type", "keyword")
      .endObject()
      .startObject(DATA_SOURCE)
        .field("type", "object")
        .field("dynamic", true)
      .endObject()
      .startObject(DEFINITION_TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_DELETED)
        .field("type", "boolean")
      .endObject();
    // @formatter:on
  }
}
