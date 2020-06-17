/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class AlertIndexV2 extends DefaultIndexMappingCreator {

  public static final int VERSION = 2;

  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String LAST_MODIFIED = "lastModified";
  private static final String CREATED = "created";
  private static final String OWNER = "owner";
  private static final String LAST_MODIFIER = "lastModifier";
  private static final String REPORT_ID = "reportId";
  private static final String EMAIL = "email";
  private static final String WEBHOOK = "webhook";
  private static final String THRESHOLD = "threshold";
  private static final String THRESHOLD_OPERATOR = "thresholdOperator";
  private static final String FIX_NOTIFICATION = "fixNotification";

  private static final String CHECK_INTERVAL = "checkInterval";
  private static final String REMINDER_INTERVAL = "reminder";
  private static final String TRIGGERED = "triggered";

  private static final String INTERVAL_VALUE = "value";
  private static final String INTERVAL_UNIT = "unit";


  @Override
  public String getIndexName() {
    return ElasticsearchConstants.ALERT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(CREATED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(REPORT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(EMAIL)
        .field("type", "keyword")
      .endObject()
      .startObject(WEBHOOK)
        .field("type", "keyword")
      .endObject()
      .startObject(THRESHOLD_OPERATOR)
       .field("type", "keyword")
      .endObject()
      .startObject(FIX_NOTIFICATION)
       .field("type", "boolean")
      .endObject()
      .startObject(THRESHOLD)
        .field("type", "long")
      .endObject()

      .startObject(TRIGGERED)
        .field("type", "boolean")
      .endObject()

      .startObject(CHECK_INTERVAL)
        .field("type", "nested")
        .startObject("properties")
          .startObject(INTERVAL_VALUE)
            .field("type", "integer")
          .endObject()
          .startObject(INTERVAL_UNIT)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject()

      .startObject(REMINDER_INTERVAL)
        .field("type", "nested")
        .startObject("properties")
          .startObject(INTERVAL_VALUE)
            .field("type", "integer")
          .endObject()
          .startObject(INTERVAL_UNIT)
            .field("type", "keyword")
          .endObject()
        .endObject()
      .endObject();
  }
}
