/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

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
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(VARIABLE_ID, p -> p.keyword(k -> k))
        .properties(VARIABLE_NAME, p -> p.keyword(k -> k))
        .properties(VARIABLE_TYPE, p -> p.keyword(k -> k))
        .properties(VARIABLE_VALUE, p -> p.object(k -> k.enabled(false)))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(INGESTION_TIMESTAMP, p -> p.date(k -> k))
        .properties(SERIALIZATION_DATA_FORMAT, p -> p.keyword(k -> k));
  }
}
