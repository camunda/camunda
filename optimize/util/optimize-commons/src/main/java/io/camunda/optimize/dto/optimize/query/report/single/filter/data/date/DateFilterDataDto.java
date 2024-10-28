/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date;

import static io.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import java.time.OffsetDateTime;

/**
 * Abstract class that contains a hidden "type" field to distinguish which filter type the jackson
 * object mapper should transform the object to.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FixedDateFilterDataDto.class, name = FIXED_DATE_FILTER),
  @JsonSubTypes.Type(value = RollingDateFilterDataDto.class, name = ROLLING_DATE_FILTER),
  @JsonSubTypes.Type(value = RelativeDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
})
public abstract class DateFilterDataDto<START> implements FilterDataDto {

  protected DateFilterType type;

  protected START start;
  protected OffsetDateTime end;

  protected boolean includeUndefined;
  protected boolean excludeUndefined;

  protected DateFilterDataDto(
      final DateFilterType type, final START start, final OffsetDateTime end) {
    this.type = type;
    this.start = start;
    this.end = end;
  }

  public DateFilterType getType() {
    return this.type;
  }

  public START getStart() {
    return this.start;
  }

  public OffsetDateTime getEnd() {
    return this.end;
  }

  public boolean isIncludeUndefined() {
    return this.includeUndefined;
  }

  public boolean isExcludeUndefined() {
    return this.excludeUndefined;
  }

  public void setType(final DateFilterType type) {
    this.type = type;
  }

  public void setStart(final START start) {
    this.start = start;
  }

  public void setEnd(final OffsetDateTime end) {
    this.end = end;
  }

  public void setIncludeUndefined(final boolean includeUndefined) {
    this.includeUndefined = includeUndefined;
  }

  public void setExcludeUndefined(final boolean excludeUndefined) {
    this.excludeUndefined = excludeUndefined;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DateFilterDataDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String type = "type";
    public static final String start = "start";
    public static final String end = "end";
    public static final String includeUndefined = "includeUndefined";
    public static final String excludeUndefined = "excludeUndefined";
  }
}
