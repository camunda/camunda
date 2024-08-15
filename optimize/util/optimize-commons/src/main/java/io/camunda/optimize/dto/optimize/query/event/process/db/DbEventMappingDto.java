/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.db;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventMappingDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;

public class DbEventMappingDto implements OptimizeDto {

  String flowNodeId;
  EventTypeDto start;
  EventTypeDto end;

  public DbEventMappingDto(
      final String flowNodeId, final EventTypeDto start, final EventTypeDto end) {
    this.flowNodeId = flowNodeId;
    this.start = start;
    this.end = end;
  }

  public DbEventMappingDto() {}

  public static DbEventMappingDto fromEventMappingDto(
      final String flowNodeId, final EventMappingDto eventMappingDto) {
    return DbEventMappingDto.builder()
        .flowNodeId(flowNodeId)
        .start(eventMappingDto.getStart())
        .end(eventMappingDto.getEnd())
        .build();
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public EventTypeDto getStart() {
    return start;
  }

  public void setStart(final EventTypeDto start) {
    this.start = start;
  }

  public EventTypeDto getEnd() {
    return end;
  }

  public void setEnd(final EventTypeDto end) {
    this.end = end;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DbEventMappingDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $flowNodeId = getFlowNodeId();
    result = result * PRIME + ($flowNodeId == null ? 43 : $flowNodeId.hashCode());
    final Object $start = getStart();
    result = result * PRIME + ($start == null ? 43 : $start.hashCode());
    final Object $end = getEnd();
    result = result * PRIME + ($end == null ? 43 : $end.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DbEventMappingDto)) {
      return false;
    }
    final DbEventMappingDto other = (DbEventMappingDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$flowNodeId = getFlowNodeId();
    final Object other$flowNodeId = other.getFlowNodeId();
    if (this$flowNodeId == null
        ? other$flowNodeId != null
        : !this$flowNodeId.equals(other$flowNodeId)) {
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
    return true;
  }

  public static DbEventMappingDtoBuilder builder() {
    return new DbEventMappingDtoBuilder();
  }

  public static final class Fields {

    public static final String flowNodeId = "flowNodeId";
    public static final String start = "start";
    public static final String end = "end";
  }

  public static class DbEventMappingDtoBuilder {

    private String flowNodeId;
    private EventTypeDto start;
    private EventTypeDto end;

    DbEventMappingDtoBuilder() {}

    public DbEventMappingDtoBuilder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public DbEventMappingDtoBuilder start(final EventTypeDto start) {
      this.start = start;
      return this;
    }

    public DbEventMappingDtoBuilder end(final EventTypeDto end) {
      this.end = end;
      return this;
    }

    public DbEventMappingDto build() {
      return new DbEventMappingDto(flowNodeId, start, end);
    }

    @Override
    public String toString() {
      return "DbEventMappingDto.DbEventMappingDtoBuilder(flowNodeId="
          + flowNodeId
          + ", start="
          + start
          + ", end="
          + end
          + ")";
    }
  }
}
