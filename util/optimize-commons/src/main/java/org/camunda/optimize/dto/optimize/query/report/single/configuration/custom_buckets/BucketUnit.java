/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_HOUR;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MILLISECOND;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MINUTE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MONTH;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_SECOND;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_WEEK;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_YEAR;

public enum BucketUnit {
  YEAR(DATE_UNIT_YEAR),
  MONTH(DATE_UNIT_MONTH),
  WEEK(DATE_UNIT_WEEK),
  DAY(DATE_UNIT_DAY),
  HOUR(DATE_UNIT_HOUR),
  MINUTE(DATE_UNIT_MINUTE),
  SECOND(DATE_UNIT_SECOND),
  MILLISECOND(DATE_UNIT_MILLISECOND),
  ;

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
