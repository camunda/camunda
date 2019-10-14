/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

@Component
public class VariableTemplate extends AbstractTemplateCreator implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "variable";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String NAME = "name";
  public static final String VALUE = "value";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
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
      .startObject(WORKFLOW_INSTANCE_KEY)
        .field("type", "long")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VALUE)
        .field("type", "keyword")
      .endObject()
      .startObject(SCOPE_KEY)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }

}
