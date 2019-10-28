/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportPositionIndex extends AbstractIndexCreator {

  public static final String INDEX_NAME = "import-position";

  public static final String ALIAS_NAME = "aliasName";
  public static final String ID = "id";
  public static final String POSITION = "position";
  public static final String FIELD_INDEX_NAME = "indexName";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getIndexName() {
    return String.format("%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), INDEX_NAME);
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
      .startObject(ID)
      .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(POSITION)
        .field("type", "long")
      .endObject()
      .startObject(ALIAS_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(FIELD_INDEX_NAME)
        .field("type", "keyword")
      .endObject();

    return newBuilder;
  }

}
