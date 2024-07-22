/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices;

import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class EventIndexV4 extends DefaultIndexMappingCreator<XContentBuilder> {

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return "event";
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return "-000001";
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
    // only include sample field since this index is used for deletion IT
    // @formatter:off
    return xContentBuilder.startObject("ID").field("type", "keyword").endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
