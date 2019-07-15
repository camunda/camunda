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
public class UserTemplate extends AbstractTemplateCreator {

  private static final String INDEX_NAME = "user";
  public static final String ID = "id";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ROLES = "roles";

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
        .startObject(USERNAME)
          .field("type","keyword")
        .endObject()
        .startObject(PASSWORD)
           .field("type","keyword")
        .endObject()
        .startObject(ROLES)
          .field("type","keyword")
        .endObject();
      return newBuilder;
  }

}