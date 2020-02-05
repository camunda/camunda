/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

@Component
public class VariableUpdateInstanceIndex extends StrictIndexMappingCreator {

  private static final String INSTANCE_ID = VariableUpdateInstanceDto.Fields.instanceId;
  private static final String NAME = VariableUpdateInstanceDto.Fields.name;
  private static final String TYPE = VariableUpdateInstanceDto.Fields.type;
  private static final String VALUE = VariableUpdateInstanceDto.Fields.value;
  private static final String PROCESS_INSTANCE_ID = VariableUpdateInstanceDto.Fields.processInstanceId;
  private static final String TENANT_ID = VariableUpdateInstanceDto.Fields.tenantId;
  private static final String TIMESTAMP = VariableUpdateInstanceDto.Fields.timestamp;

  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
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
      .endObject()
      ;
    // @formatter:on
  }

  @Override
  public XContentBuilder getCustomSettings(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, TIMESTAMP)
        .field(SORT_ORDER_SETTING, "asc")
      .endObject();
    // @formatter:on
  }

}
