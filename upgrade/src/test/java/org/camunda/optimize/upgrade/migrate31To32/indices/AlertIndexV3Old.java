/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32.indices;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class AlertIndexV3Old extends DefaultIndexMappingCreator {

  private static final int VERSION = 3;

  private static final String ID = AlertDefinitionDto.Fields.id;
  private static final String NAME = AlertDefinitionDto.Fields.name;
  private static final String LAST_MODIFIED = AlertDefinitionDto.Fields.lastModified;
  private static final String CREATED = AlertDefinitionDto.Fields.created;
  private static final String OWNER = AlertDefinitionDto.Fields.owner;
  private static final String LAST_MODIFIER = AlertDefinitionDto.Fields.lastModifier;
  private static final String REPORT_ID = AlertDefinitionDto.Fields.reportId;
  private static final String EMAIL = "email";
  private static final String WEBHOOK = AlertDefinitionDto.Fields.webhook;
  private static final String THRESHOLD = AlertDefinitionDto.Fields.threshold;
  private static final String THRESHOLD_OPERATOR = AlertDefinitionDto.Fields.thresholdOperator;
  private static final String FIX_NOTIFICATION = AlertDefinitionDto.Fields.fixNotification;

  private static final String CHECK_INTERVAL = AlertDefinitionDto.Fields.checkInterval;
  private static final String REMINDER_INTERVAL = AlertDefinitionDto.Fields.reminder;
  private static final String TRIGGERED = AlertDefinitionDto.Fields.triggered;

  private static final String INTERVAL_VALUE = AlertInterval.Fields.value;
  private static final String INTERVAL_UNIT = AlertInterval.Fields.unit;

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
