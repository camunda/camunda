/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.indices;

import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class RenameFieldTestIndex extends DefaultIndexMappingCreator<XContentBuilder> {
  private static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return "users";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder.startObject("name").field("type", "keyword").endObject();
  }

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }
}
