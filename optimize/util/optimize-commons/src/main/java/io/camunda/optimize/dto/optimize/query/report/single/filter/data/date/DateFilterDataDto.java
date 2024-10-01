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

  public void setType(DateFilterType type) {
    this.type = type;
  }

  public void setStart(START start) {
    this.start = start;
  }

  public void setEnd(OffsetDateTime end) {
    this.end = end;
  }

  public void setIncludeUndefined(boolean includeUndefined) {
    this.includeUndefined = includeUndefined;
  }

  public void setExcludeUndefined(boolean excludeUndefined) {
    this.excludeUndefined = excludeUndefined;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DateFilterDataDto)) {
      return false;
    }
    final DateFilterDataDto<?> other = (DateFilterDataDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = this.getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$start = this.getStart();
    final Object other$start = other.getStart();
    if (this$start == null ? other$start != null : !this$start.equals(other$start)) {
      return false;
    }
    final Object this$end = this.getEnd();
    final Object other$end = other.getEnd();
    if (this$end == null ? other$end != null : !this$end.equals(other$end)) {
      return false;
    }
    if (this.isIncludeUndefined() != other.isIncludeUndefined()) {
      return false;
    }
    if (this.isExcludeUndefined() != other.isExcludeUndefined()) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DateFilterDataDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $start = this.getStart();
    result = result * PRIME + ($start == null ? 43 : $start.hashCode());
    final Object $end = this.getEnd();
    result = result * PRIME + ($end == null ? 43 : $end.hashCode());
    result = result * PRIME + (this.isIncludeUndefined() ? 79 : 97);
    result = result * PRIME + (this.isExcludeUndefined() ? 79 : 97);
    return result;
  }

  public static final class Fields {

    public static final String type = "type";
    public static final String start = "start";
    public static final String end = "end";
    public static final String includeUndefined = "includeUndefined";
    public static final String excludeUndefined = "excludeUndefined";
  }
}
