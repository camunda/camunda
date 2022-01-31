/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class AlertIndex extends DefaultIndexMappingCreator {

  public static final int VERSION = 4;

  public static final String ID = AlertDefinitionDto.Fields.id;
  public static final String NAME = AlertDefinitionDto.Fields.name;
  public static final String LAST_MODIFIED = AlertDefinitionDto.Fields.lastModified;
  public static final String CREATED = AlertDefinitionDto.Fields.created;
  public static final String OWNER = AlertDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = AlertDefinitionDto.Fields.lastModifier;
  public static final String REPORT_ID = AlertDefinitionDto.Fields.reportId;
  public static final String EMAILS = AlertDefinitionDto.Fields.emails;
  public static final String WEBHOOK = AlertDefinitionDto.Fields.webhook;
  public static final String THRESHOLD = AlertDefinitionDto.Fields.threshold;
  public static final String THRESHOLD_OPERATOR = AlertDefinitionDto.Fields.thresholdOperator;
  public static final String FIX_NOTIFICATION = AlertDefinitionDto.Fields.fixNotification;

  public static final String CHECK_INTERVAL = AlertDefinitionDto.Fields.checkInterval;
  public static final String REMINDER_INTERVAL = AlertDefinitionDto.Fields.reminder;
  public static final String TRIGGERED = AlertDefinitionDto.Fields.triggered;

  public static final String INTERVAL_VALUE = AlertInterval.Fields.value;
  public static final String INTERVAL_UNIT = AlertInterval.Fields.unit;

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
    // @formatter:off
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
      .startObject(EMAILS)
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
        .field("type", "double")
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
    // @formatter:on
  }

}
