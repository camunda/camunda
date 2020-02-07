/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class BusinessKeyIndex extends StrictIndexMappingCreator {

  public static final int VERSION = 1;

  public static final String BUSINESS_KEY = BusinessKeyDto.Fields.businessKey;
  public static final String PROCESS_INSTANCE_ID = BusinessKeyDto.Fields.processInstanceId;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.BUSINESS_KEY_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(PROCESS_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(BUSINESS_KEY)
        .field("type", "keyword")
      .endObject();
    // @formatter:on
  }

}
