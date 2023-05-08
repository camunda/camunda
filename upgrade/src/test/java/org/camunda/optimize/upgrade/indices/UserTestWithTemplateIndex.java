/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.indices;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

@AllArgsConstructor
public class UserTestWithTemplateIndex extends DefaultIndexMappingCreator {

  private static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return "users";
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
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject("password")
      .field("type", "keyword")
      .endObject()
      .startObject("username")
      .field("type", "keyword")
      .endObject();
  }
}
