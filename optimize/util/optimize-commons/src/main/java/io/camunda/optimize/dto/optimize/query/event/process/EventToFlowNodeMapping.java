/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import java.util.List;

public class EventToFlowNodeMapping {

  private String eventIdentifier;
  private MappedEventType mappedAs;
  private String flowNodeId;
  private String flowNodeType;
  private List<String> previousMappedFlowNodeIds;
  private List<String> nextMappedFlowNodeIds;

  public EventToFlowNodeMapping(
      final String eventIdentifier,
      final MappedEventType mappedAs,
      final String flowNodeId,
      final String flowNodeType,
      final List<String> previousMappedFlowNodeIds,
      final List<String> nextMappedFlowNodeIds) {
    this.eventIdentifier = eventIdentifier;
    this.mappedAs = mappedAs;
    this.flowNodeId = flowNodeId;
    this.flowNodeType = flowNodeType;
    this.previousMappedFlowNodeIds = previousMappedFlowNodeIds;
    this.nextMappedFlowNodeIds = nextMappedFlowNodeIds;
  }

  protected EventToFlowNodeMapping() {}

  public String getEventIdentifier() {
    return eventIdentifier;
  }

  public void setEventIdentifier(final String eventIdentifier) {
    this.eventIdentifier = eventIdentifier;
  }

  public MappedEventType getMappedAs() {
    return mappedAs;
  }

  public void setMappedAs(final MappedEventType mappedAs) {
    this.mappedAs = mappedAs;
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

  public List<String> getPreviousMappedFlowNodeIds() {
    return previousMappedFlowNodeIds;
  }

  public void setPreviousMappedFlowNodeIds(final List<String> previousMappedFlowNodeIds) {
    this.previousMappedFlowNodeIds = previousMappedFlowNodeIds;
  }

  public List<String> getNextMappedFlowNodeIds() {
    return nextMappedFlowNodeIds;
  }

  public void setNextMappedFlowNodeIds(final List<String> nextMappedFlowNodeIds) {
    this.nextMappedFlowNodeIds = nextMappedFlowNodeIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventToFlowNodeMapping;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $eventIdentifier = getEventIdentifier();
    result = result * PRIME + ($eventIdentifier == null ? 43 : $eventIdentifier.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventToFlowNodeMapping)) {
      return false;
    }
    final EventToFlowNodeMapping other = (EventToFlowNodeMapping) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$eventIdentifier = getEventIdentifier();
    final Object other$eventIdentifier = other.getEventIdentifier();
    if (this$eventIdentifier == null
        ? other$eventIdentifier != null
        : !this$eventIdentifier.equals(other$eventIdentifier)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventToFlowNodeMapping(eventIdentifier="
        + getEventIdentifier()
        + ", mappedAs="
        + getMappedAs()
        + ", flowNodeId="
        + getFlowNodeId()
        + ", flowNodeType="
        + getFlowNodeType()
        + ", previousMappedFlowNodeIds="
        + getPreviousMappedFlowNodeIds()
        + ", nextMappedFlowNodeIds="
        + getNextMappedFlowNodeIds()
        + ")";
  }

  public static EventToFlowNodeMappingBuilder builder() {
    return new EventToFlowNodeMappingBuilder();
  }

  public static final class Fields {

    public static final String eventIdentifier = "eventIdentifier";
    public static final String mappedAs = "mappedAs";
    public static final String flowNodeId = "flowNodeId";
    public static final String flowNodeType = "flowNodeType";
    public static final String previousMappedFlowNodeIds = "previousMappedFlowNodeIds";
    public static final String nextMappedFlowNodeIds = "nextMappedFlowNodeIds";
  }

  public static class EventToFlowNodeMappingBuilder {

    private String eventIdentifier;
    private MappedEventType mappedAs;
    private String flowNodeId;
    private String flowNodeType;
    private List<String> previousMappedFlowNodeIds;
    private List<String> nextMappedFlowNodeIds;

    EventToFlowNodeMappingBuilder() {}

    public EventToFlowNodeMappingBuilder eventIdentifier(final String eventIdentifier) {
      this.eventIdentifier = eventIdentifier;
      return this;
    }

    public EventToFlowNodeMappingBuilder mappedAs(final MappedEventType mappedAs) {
      this.mappedAs = mappedAs;
      return this;
    }

    public EventToFlowNodeMappingBuilder flowNodeId(final String flowNodeId) {
      this.flowNodeId = flowNodeId;
      return this;
    }

    public EventToFlowNodeMappingBuilder flowNodeType(final String flowNodeType) {
      this.flowNodeType = flowNodeType;
      return this;
    }

    public EventToFlowNodeMappingBuilder previousMappedFlowNodeIds(
        final List<String> previousMappedFlowNodeIds) {
      this.previousMappedFlowNodeIds = previousMappedFlowNodeIds;
      return this;
    }

    public EventToFlowNodeMappingBuilder nextMappedFlowNodeIds(
        final List<String> nextMappedFlowNodeIds) {
      this.nextMappedFlowNodeIds = nextMappedFlowNodeIds;
      return this;
    }

    public EventToFlowNodeMapping build() {
      return new EventToFlowNodeMapping(
          eventIdentifier,
          mappedAs,
          flowNodeId,
          flowNodeType,
          previousMappedFlowNodeIds,
          nextMappedFlowNodeIds);
    }

    @Override
    public String toString() {
      return "EventToFlowNodeMapping.EventToFlowNodeMappingBuilder(eventIdentifier="
          + eventIdentifier
          + ", mappedAs="
          + mappedAs
          + ", flowNodeId="
          + flowNodeId
          + ", flowNodeType="
          + flowNodeType
          + ", previousMappedFlowNodeIds="
          + previousMappedFlowNodeIds
          + ", nextMappedFlowNodeIds="
          + nextMappedFlowNodeIds
          + ")";
    }
  }
}
