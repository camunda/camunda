/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;

public class FlowNodeInstanceUpdateDto implements OptimizeDto {

  protected String sourceEventId;
  protected String flowNodeId;
  protected String flowNodeType;
  protected MappedEventType mappedAs;
  protected OffsetDateTime date;

  public FlowNodeInstanceUpdateDto(
      final String sourceEventId,
      final String flowNodeId,
      final String flowNodeType,
      final MappedEventType mappedAs,
      final OffsetDateTime date) {
    this.sourceEventId = sourceEventId;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.mappedAs = mappedAs;
    this.date = date;
  }

  protected FlowNodeInstanceUpdateDto() {}

  public String getId() {
    return sourceEventId + ":" + flowNodeId;
  }

  public String getSourceEventId() {
    return sourceEventId;
  }

  public void setSourceEventId(final String sourceEventId) {
    this.sourceEventId = sourceEventId;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public void setFlowNodeId(final String flowNodeId) {
    this.flowNodeId = flowNodeId;
  }

  public String getFlowNodeType() {
    return flowNodeType;
  }

  public void setFlowNodeType(final String flowNodeType) {
    this.flowNodeType = flowNodeType;
  }

  public MappedEventType getMappedAs() {
    return mappedAs;
  }

  public void setMappedAs(final MappedEventType mappedAs) {
    this.mappedAs = mappedAs;
  }

  public OffsetDateTime getDate() {
    return date;
  }

  public void setDate(final OffsetDateTime date) {
    this.date = date;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FlowNodeInstanceUpdateDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $sourceEventId = getSourceEventId();
    result = result * PRIME + ($sourceEventId == null ? 43 : $sourceEventId.hashCode());
    final Object $flowNodeId = getFlowNodeId();
    result = result * PRIME + ($flowNodeId == null ? 43 : $flowNodeId.hashCode());
    final Object $flowNodeType = getFlowNodeType();
    result = result * PRIME + ($flowNodeType == null ? 43 : $flowNodeType.hashCode());
    final Object $mappedAs = getMappedAs();
    result = result * PRIME + ($mappedAs == null ? 43 : $mappedAs.hashCode());
    final Object $date = getDate();
    result = result * PRIME + ($date == null ? 43 : $date.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof FlowNodeInstanceUpdateDto)) {
      return false;
    }
    final FlowNodeInstanceUpdateDto other = (FlowNodeInstanceUpdateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$sourceEventId = getSourceEventId();
    final Object other$sourceEventId = other.getSourceEventId();
    if (this$sourceEventId == null
        ? other$sourceEventId != null
        : !this$sourceEventId.equals(other$sourceEventId)) {
      return false;
    }
    final Object this$flowNodeId = getFlowNodeId();
    final Object other$flowNodeId = other.getFlowNodeId();
    if (this$flowNodeId == null
        ? other$flowNodeId != null
        : !this$flowNodeId.equals(other$flowNodeId)) {
      return false;
    }
    final Object this$flowNodeType = getFlowNodeType();
    final Object other$flowNodeType = other.getFlowNodeType();
    if (this$flowNodeType == null
        ? other$flowNodeType != null
        : !this$flowNodeType.equals(other$flowNodeType)) {
      return false;
    }
    final Object this$mappedAs = getMappedAs();
    final Object other$mappedAs = other.getMappedAs();
    if (this$mappedAs == null ? other$mappedAs != null : !this$mappedAs.equals(other$mappedAs)) {
      return false;
    }
    final Object this$date = getDate();
    final Object other$date = other.getDate();
    if (this$date == null ? other$date != null : !this$date.equals(other$date)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "FlowNodeInstanceUpdateDto(sourceEventId="
        + getSourceEventId()
        + ", flowNodeId="
        + getFlowNodeId()
        + ", flowNodeType="
        + getFlowNodeType()
        + ", mappedAs="
        + getMappedAs()
        + ", date="
        + getDate()
        + ")";
  }

  public static FlowNodeInstanceUpdateDtoBuilder builder() {
    return new FlowNodeInstanceUpdateDtoBuilder();
  }

  public static final class Fields {

    public static final String sourceEventId = "sourceEventId";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeType = "flowNodeType";
    public static final String mappedAs = "mappedAs";
    public static final String date = "date";
  }

  public static class FlowNodeInstanceUpdateDtoBuilder {

    private String sourceEventId;
    private String flowNodeId;
    private String flowNodeType;
    private MappedEventType mappedAs;
    private OffsetDateTime date;

    FlowNodeInstanceUpdateDtoBuilder() {}

    public FlowNodeInstanceUpdateDtoBuilder sourceEventId(final String sourceEventId) {
      this.sourceEventId = sourceEventId;
      return this;
    }

    public FlowNodeInstanceUpdateDtoBuilder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public FlowNodeInstanceUpdateDtoBuilder flowNodeType(final String flowNodeType) {
      this.flowNodeType = flowNodeType;
      return this;
    }

    public FlowNodeInstanceUpdateDtoBuilder mappedAs(final MappedEventType mappedAs) {
      this.mappedAs = mappedAs;
      return this;
    }

    public FlowNodeInstanceUpdateDtoBuilder date(final OffsetDateTime date) {
      this.date = date;
      return this;
    }

    public FlowNodeInstanceUpdateDto build() {
      return new FlowNodeInstanceUpdateDto(sourceEventId, flowNodeId, flowNodeType, mappedAs, date);
    }

    @Override
    public String toString() {
      return "FlowNodeInstanceUpdateDto.FlowNodeInstanceUpdateDtoBuilder(sourceEventId="
          + sourceEventId
          + ", flowNodeId="
          + flowNodeId
          + ", flowNodeType="
          + flowNodeType
          + ", mappedAs="
          + mappedAs
          + ", date="
          + date
          + ")";
    }
  }
}
