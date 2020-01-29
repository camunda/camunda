/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static org.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FixedDateFilterDataDto.class, name = FIXED_DATE_FILTER),
  @JsonSubTypes.Type(value = RelativeDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
  @JsonSubTypes.Type(value = RollingDateFilterDataDto.class, name = ROLLING_DATE_FILTER),
})

@Getter
@Setter
public abstract class DateFilterDataDto<START> implements FilterDataDto {

  protected DateFilterType type;

  protected START start;
  protected OffsetDateTime end;
}
