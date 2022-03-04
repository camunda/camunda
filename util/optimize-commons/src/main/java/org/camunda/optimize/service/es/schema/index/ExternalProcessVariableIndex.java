/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;

public class ExternalProcessVariableIndex extends DefaultIndexMappingCreator {

  public static final String VARIABLE_ID = ExternalProcessVariableDto.Fields.variableId;
  public static final String VARIABLE_NAME = ExternalProcessVariableDto.Fields.variableName;
  public static final String VARIABLE_TYPE = ExternalProcessVariableDto.Fields.variableType;
  public static final String VARIABLE_VALUE = ExternalProcessVariableDto.Fields.variableValue;
  public static final String PROCESS_INSTANCE_ID = ExternalProcessVariableDto.Fields.processInstanceId;
  public static final String PROCESS_DEFINITION_KEY = ExternalProcessVariableDto.Fields.processDefinitionKey;
  public static final String INGESTION_TIMESTAMP = ExternalProcessVariableDto.Fields.ingestionTimestamp;
  public static final String SERIALIZATION_DATA_FORMAT = ExternalProcessVariableDto.Fields.serializationDataFormat;

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(VARIABLE_ID)
        .field("type", TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_NAME)
        .field("type", TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_TYPE)
        .field("type", TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field(MAPPING_ENABLED_SETTING, false)
      .endObject()
      .startObject(PROCESS_INSTANCE_ID)
        .field("type", TYPE_KEYWORD)
      .endObject()
      .startObject(PROCESS_DEFINITION_KEY)
        .field("type", TYPE_KEYWORD)
      .endObject()
      .startObject(INGESTION_TIMESTAMP)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
      .endObject()
      .startObject(SERIALIZATION_DATA_FORMAT)
        .field("type", TYPE_KEYWORD)
      .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder = super.getStaticSettings(xContentBuilder, configurationService);
    return newXContentBuilder
      .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, INGESTION_TIMESTAMP)
        .field(SORT_ORDER_SETTING, "desc")
      .endObject();
    // @formatter:on
  }
}
