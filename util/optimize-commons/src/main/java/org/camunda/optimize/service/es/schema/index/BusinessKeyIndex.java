/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class BusinessKeyIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 2;

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
