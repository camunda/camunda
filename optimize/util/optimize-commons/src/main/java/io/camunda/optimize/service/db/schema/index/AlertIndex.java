/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class AlertIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 4;

  public static final String ID = AlertDefinitionDto.Fields.id;
  public static final String NAME = AlertCreationRequestDto.Fields.name;
  public static final String LAST_MODIFIED = AlertDefinitionDto.Fields.lastModified;
  public static final String CREATED = AlertDefinitionDto.Fields.created;
  public static final String OWNER = AlertDefinitionDto.Fields.owner;
  public static final String LAST_MODIFIER = AlertDefinitionDto.Fields.lastModifier;
  public static final String REPORT_ID = AlertCreationRequestDto.Fields.reportId;
  public static final String EMAILS = AlertCreationRequestDto.Fields.emails;
  public static final String WEBHOOK = AlertCreationRequestDto.Fields.webhook;
  public static final String THRESHOLD = AlertCreationRequestDto.Fields.threshold;
  public static final String THRESHOLD_OPERATOR = AlertCreationRequestDto.Fields.thresholdOperator;
  public static final String FIX_NOTIFICATION = AlertCreationRequestDto.Fields.fixNotification;

  public static final String CHECK_INTERVAL = AlertCreationRequestDto.Fields.checkInterval;
  public static final String REMINDER_INTERVAL = AlertCreationRequestDto.Fields.reminder;
  public static final String TRIGGERED = AlertDefinitionDto.Fields.triggered;

  public static final String INTERVAL_VALUE = AlertInterval.Fields.value;
  public static final String INTERVAL_UNIT = AlertInterval.Fields.unit;

  @Override
  public String getIndexName() {
    return DatabaseConstants.ALERT_INDEX_NAME;
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
