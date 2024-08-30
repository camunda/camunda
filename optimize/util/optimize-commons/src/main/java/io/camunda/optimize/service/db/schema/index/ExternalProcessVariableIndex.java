/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_ENABLED_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_DATE;
import static io.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;

import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class ExternalProcessVariableIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String VARIABLE_ID = ExternalProcessVariableDto.Fields.variableId;
  public static final String VARIABLE_NAME = ExternalProcessVariableDto.Fields.variableName;
  public static final String VARIABLE_TYPE = ExternalProcessVariableDto.Fields.variableType;
  public static final String VARIABLE_VALUE = ExternalProcessVariableDto.Fields.variableValue;
  public static final String PROCESS_INSTANCE_ID =
      ExternalProcessVariableDto.Fields.processInstanceId;
  public static final String PROCESS_DEFINITION_KEY =
      ExternalProcessVariableDto.Fields.processDefinitionKey;
  public static final String INGESTION_TIMESTAMP =
      ExternalProcessVariableDto.Fields.ingestionTimestamp;
  public static final String SERIALIZATION_DATA_FORMAT =
      ExternalProcessVariableDto.Fields.serializationDataFormat;

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
    return DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
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
}
