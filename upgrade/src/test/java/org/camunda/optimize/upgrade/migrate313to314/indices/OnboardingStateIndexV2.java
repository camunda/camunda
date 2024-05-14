/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate313to314.indices;

import java.io.IOException;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

public class OnboardingStateIndexV2 extends DefaultIndexMappingCreator<XContentBuilder> {

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return "onboarding-state";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject("id")
        .field("type", "keyword")
        .endObject()
        .startObject("key")
        .field("type", "keyword")
        .endObject()
        .startObject("userId")
        .field("type", "keyword")
        .endObject()
        .startObject("seen")
        .field("type", "boolean")
        .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
