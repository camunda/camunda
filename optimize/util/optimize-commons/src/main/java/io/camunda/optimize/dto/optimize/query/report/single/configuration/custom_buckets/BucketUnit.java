/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets;

import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_DAY;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_HOUR;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MILLISECOND;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MINUTE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MONTH;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_SECOND;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_WEEK;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_YEAR;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BucketUnit {
  YEAR(DATE_UNIT_YEAR),
  MONTH(DATE_UNIT_MONTH),
  WEEK(DATE_UNIT_WEEK),
  DAY(DATE_UNIT_DAY),
  HOUR(DATE_UNIT_HOUR),
  MINUTE(DATE_UNIT_MINUTE),
  SECOND(DATE_UNIT_SECOND),
  MILLISECOND(DATE_UNIT_MILLISECOND);

  private final String id;

  BucketUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
