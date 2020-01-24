/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.indexes;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

@AllArgsConstructor
public class UserTestWithTemplateUpdatedMappingIndex extends StrictIndexMappingCreator {

  private static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return "users";
  }

  @Override
  public String getIndexNameSuffix() {
    return ElasticsearchConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public Boolean getCreateFromTemplate() {
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
      .endObject()
      .startObject("email")
      .field("type", "keyword")
      .endObject();
  }
}
