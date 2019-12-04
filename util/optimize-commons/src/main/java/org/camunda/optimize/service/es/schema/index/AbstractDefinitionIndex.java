/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class AbstractDefinitionIndex extends StrictIndexMappingCreator {
  public static final String DEFINITION_ID = DefinitionOptimizeDto.Fields.id;
  public static final String DEFINITION_KEY = DefinitionOptimizeDto.Fields.key;
  public static final String DEFINITION_VERSION = DefinitionOptimizeDto.Fields.version;
  public static final String DEFINITION_VERSION_TAG = DefinitionOptimizeDto.Fields.versionTag;
  public static final String DEFINITION_NAME = DefinitionOptimizeDto.Fields.name;
  public static final String DEFINITION_ENGINE = DefinitionOptimizeDto.Fields.engine;
  public static final String DEFINITION_TENANT_ID = DefinitionOptimizeDto.Fields.tenantId;

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
      .startObject(DEFINITION_ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_TENANT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(DEFINITION_NAME)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }
}
