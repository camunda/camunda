/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_FIELD_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_ORDER_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SORT_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME;

public class VariableUpdateInstanceIndex extends DefaultIndexMappingCreator {

  public static final String INSTANCE_ID = VariableUpdateInstanceDto.Fields.instanceId;
  public static final String NAME = VariableUpdateInstanceDto.Fields.name;
  public static final String TYPE = VariableUpdateInstanceDto.Fields.type;
  public static final String VALUE = VariableUpdateInstanceDto.Fields.value;
  public static final String PROCESS_INSTANCE_ID = VariableUpdateInstanceDto.Fields.processInstanceId;
  public static final String TENANT_ID = VariableUpdateInstanceDto.Fields.tenantId;
  public static final String TIMESTAMP = VariableUpdateInstanceDto.Fields.timestamp;

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return VARIABLE_UPDATE_INSTANCE_INDEX_NAME;
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

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder = super.getStaticSettings(xContentBuilder, configurationService);
    return newXContentBuilder
      .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, TIMESTAMP)
        .field(SORT_ORDER_SETTING, "asc")
      .endObject();
    // @formatter:on
  }

}
