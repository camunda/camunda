/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class VariableUpdateInstanceIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final String INSTANCE_ID = VariableUpdateInstanceDto.Fields.instanceId;
  public static final String NAME = VariableUpdateInstanceDto.Fields.name;
  public static final String TYPE = VariableUpdateInstanceDto.Fields.type;
  public static final String VALUE = VariableUpdateInstanceDto.Fields.value;
  public static final String PROCESS_INSTANCE_ID =
      VariableUpdateInstanceDto.Fields.processInstanceId;
  public static final String TENANT_ID = VariableUpdateInstanceDto.Fields.tenantId;
  public static final String TIMESTAMP = VariableUpdateInstanceDto.Fields.timestamp;

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
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
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(INSTANCE_ID)
        .field("type", "keyword")
        .endObject()
        .startObject(NAME)
        .field("type", "keyword")
        .endObject()
        .startObject(TYPE)
        .field("type", "keyword")
        .endObject()
        .startObject(VALUE)
        .field("type", "keyword")
        .endObject()
        .startObject(PROCESS_INSTANCE_ID)
        .field("type", "keyword")
        .endObject()
        .startObject(TENANT_ID)
        .field("type", "keyword")
        .endObject()
        .startObject(TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .endObject();
    // @formatter:on
  }
}
