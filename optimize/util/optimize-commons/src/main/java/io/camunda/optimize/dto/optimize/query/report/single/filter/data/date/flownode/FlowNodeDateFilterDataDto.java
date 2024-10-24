/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode;

import static io.camunda.optimize.dto.optimize.ReportConstants.FIXED_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.RELATIVE_DATE_FILTER;
import static io.camunda.optimize.dto.optimize.ReportConstants.ROLLING_DATE_FILTER;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import java.time.OffsetDateTime;
import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FixedFlowNodeDateFilterDataDto.class, name = FIXED_DATE_FILTER),
  @JsonSubTypes.Type(value = RollingFlowNodeDateFilterDataDto.class, name = ROLLING_DATE_FILTER),
  @JsonSubTypes.Type(value = RelativeFlowNodeDateFilterDataDto.class, name = RELATIVE_DATE_FILTER),
})
public abstract class FlowNodeDateFilterDataDto<START> extends DateFilterDataDto<START> {

  protected List<String> flowNodeIds;

  protected FlowNodeDateFilterDataDto(
      final List<String> flowNodeIds,
      final DateFilterType type,
      final START start,
      final OffsetDateTime end) {
    super(type, start, end);
    this.flowNodeIds = flowNodeIds;
  }

  public List<String> getFlowNodeIds() {
    return flowNodeIds;
  }

  public void setFlowNodeIds(final List<String> flowNodeIds) {
    this.flowNodeIds = flowNodeIds;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeDateFilterDataDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String flowNodeIds = "flowNodeIds";
  }
}
