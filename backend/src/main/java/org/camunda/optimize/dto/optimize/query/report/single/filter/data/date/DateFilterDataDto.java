/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RelativeDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
  @JsonSubTypes.Type(value = FixedDateFilterDataDto.class, name = FIXED_DATE_FILTER),
})

public abstract class DateFilterDataDto<START> implements FilterDataDto {

  protected DateFilterType type;

  protected START start;
  protected OffsetDateTime end;

  public DateFilterType getType() {
    return type;
  }

  public void setType(DateFilterType type) {
    this.type = type;
  }

  public OffsetDateTime getEnd() {
    return end;
  }

  public void setEnd(OffsetDateTime end) {
    this.end = end;
  }

  public START getStart() {
    return start;
  }

  public void setStart(START start) {
    this.start = start;
  }
}
