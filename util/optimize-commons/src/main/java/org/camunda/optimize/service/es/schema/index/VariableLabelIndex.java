/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public class VariableLabelIndex extends DefaultIndexMappingCreator {

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
    return ElasticsearchConstants.VARIABLE_LABEL_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
