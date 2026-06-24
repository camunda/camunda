/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

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
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, Property.of(p -> p.keyword(k -> k)))
        .properties(NAME, Property.of(p -> p.keyword(k -> k)))
        .properties(LAST_MODIFIED, Property.of(p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT))))
        .properties(CREATED, Property.of(p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT))))
        .properties(OWNER, Property.of(p -> p.keyword(k -> k)))
        .properties(LAST_MODIFIER, Property.of(p -> p.keyword(k -> k)))
        .properties(REPORT_ID, Property.of(p -> p.keyword(k -> k)))
        .properties(EMAILS, Property.of(p -> p.keyword(k -> k)))
        .properties(THRESHOLD_OPERATOR, Property.of(p -> p.keyword(k -> k)))
        .properties(FIX_NOTIFICATION, Property.of(p -> p.boolean_(k -> k)))
        .properties(THRESHOLD, Property.of(p -> p.double_(k -> k)))
        .properties(TRIGGERED, Property.of(p -> p.boolean_(k -> k)))
        .properties(
            CHECK_INTERVAL,
            Property.of(
                p ->
                    p.nested(
                        k ->
                            k.properties(INTERVAL_VALUE, Property.of(v -> v.integer(i -> i)))
                                .properties(INTERVAL_UNIT, Property.of(v -> v.keyword(i -> i))))))
        .properties(
            REMINDER_INTERVAL,
            Property.of(
                p ->
                    p.nested(
                        k ->
                            k.properties(INTERVAL_VALUE, Property.of(v -> v.integer(i -> i)))
                                .properties(INTERVAL_UNIT, Property.of(v -> v.keyword(i -> i))))));
  }
}
