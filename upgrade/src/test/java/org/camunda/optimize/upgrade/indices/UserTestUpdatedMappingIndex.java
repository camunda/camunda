/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.indices;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public class UserTestUpdatedMappingIndex extends DefaultIndexMappingCreator {

  private static final int VERSION = 2;

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
    return xContentBuilder
      .startObject("password")
        .field("type", "keyword")
      .endObject()
      .startObject("username")
        .field("type", "keyword")
      .endObject()
      .startObject("email")
        .field("type", "keyword")
      .endObject();
  }
}
