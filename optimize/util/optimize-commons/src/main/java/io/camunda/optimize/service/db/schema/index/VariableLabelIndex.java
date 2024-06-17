/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_OBJECT;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_TEXT;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class VariableLabelIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    xContentBuilder
        .startObject(DefinitionVariableLabelsDto.Fields.definitionKey)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(DefinitionVariableLabelsDto.Fields.labels)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .startObject("properties")
        .startObject(LabelDto.Fields.variableLabel)
        .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
        .endObject()
        .startObject(LabelDto.Fields.variableName)
        .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
        .endObject()
        .startObject(LabelDto.Fields.variableType)
        .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
        .endObject()
        .endObject()
        .endObject();
    return xContentBuilder;
    // @formatter:on
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
