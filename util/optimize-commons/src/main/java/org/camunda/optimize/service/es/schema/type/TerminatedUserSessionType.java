/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class TerminatedUserSessionType extends StrictTypeMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String TERMINATION_TIMESTAMP = "terminationTimestamp";

  @Override
  public XContentBuilder addProperties(final XContentBuilder builder) throws IOException {
    // @formatter:off
    return  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TERMINATION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject();
    // @formatter:on
  }

  @Override
  public String getType() {
    return ElasticsearchConstants.TERMINATED_USER_SESSION_TYPE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
