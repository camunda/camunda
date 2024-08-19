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
    return type;
  }

  public DateFilterDataDto<START> setType(final DateFilterType type) {
    this.type = type;
    return this;
  }

  public START getStart() {
    return start;
  }

  public DateFilterDataDto<START> setStart(final START start) {
    this.start = start;
    return this;
  }

  public OffsetDateTime getEnd() {
    return end;
  }

  public DateFilterDataDto<START> setEnd(final OffsetDateTime end) {
    this.end = end;
    return this;
  }

  public boolean isIncludeUndefined() {
    return includeUndefined;
  }

  public DateFilterDataDto<START> setIncludeUndefined(final boolean includeUndefined) {
    this.includeUndefined = includeUndefined;
    return this;
  }

  public boolean isExcludeUndefined() {
    return excludeUndefined;
  }

  public DateFilterDataDto<START> setExcludeUndefined(final boolean excludeUndefined) {
    this.excludeUndefined = excludeUndefined;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DateFilterDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $start = getStart();
    result = result * PRIME + ($start == null ? 43 : $start.hashCode());
    final Object $end = getEnd();
    result = result * PRIME + ($end == null ? 43 : $end.hashCode());
    result = result * PRIME + (isIncludeUndefined() ? 79 : 97);
    result = result * PRIME + (isExcludeUndefined() ? 79 : 97);
    return result;
  }

  @Override
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
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$start = getStart();
    final Object other$start = other.getStart();
    if (this$start == null ? other$start != null : !this$start.equals(other$start)) {
      return false;
    }
    final Object this$end = getEnd();
    final Object other$end = other.getEnd();
    if (this$end == null ? other$end != null : !this$end.equals(other$end)) {
      return false;
    }
    if (isIncludeUndefined() != other.isIncludeUndefined()) {
      return false;
    }
    if (isExcludeUndefined() != other.isExcludeUndefined()) {
      return false;
    }
    return true;
  }

  public static final class Fields {

    public static final String type = "type";
    public static final String start = "start";
    public static final String end = "end";
    public static final String includeUndefined = "includeUndefined";
    public static final String excludeUndefined = "excludeUndefined";
  }
}
