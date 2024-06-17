/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class BusinessKeyIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 2;

  public static final String BUSINESS_KEY = BusinessKeyDto.Fields.businessKey;
  public static final String PROCESS_INSTANCE_ID = BusinessKeyDto.Fields.processInstanceId;

  @Override
  public String getIndexName() {
    return DatabaseConstants.BUSINESS_KEY_INDEX_NAME;
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
