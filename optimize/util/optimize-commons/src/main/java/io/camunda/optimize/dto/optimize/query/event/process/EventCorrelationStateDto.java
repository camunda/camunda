/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class EventCorrelationStateDto {

  private Map<MappedEventType, Set<String>> correlatedAsToFlowNodeInstanceIds =
      new EnumMap<>(MappedEventType.class);

  public EventCorrelationStateDto(
      final Map<MappedEventType, Set<String>> correlatedAsToFlowNodeInstanceIds) {
    this.correlatedAsToFlowNodeInstanceIds = correlatedAsToFlowNodeInstanceIds;
  }

  public EventCorrelationStateDto() {}

  public Map<MappedEventType, Set<String>> getCorrelatedAsToFlowNodeInstanceIds() {
    return correlatedAsToFlowNodeInstanceIds;
  }

  public void setCorrelatedAsToFlowNodeInstanceIds(
      final Map<MappedEventType, Set<String>> correlatedAsToFlowNodeInstanceIds) {
    this.correlatedAsToFlowNodeInstanceIds = correlatedAsToFlowNodeInstanceIds;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventCorrelationStateDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $correlatedAsToFlowNodeInstanceIds = getCorrelatedAsToFlowNodeInstanceIds();
    result =
        result * PRIME
            + ($correlatedAsToFlowNodeInstanceIds == null
                ? 43
                : $correlatedAsToFlowNodeInstanceIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventCorrelationStateDto)) {
      return false;
    }
    final EventCorrelationStateDto other = (EventCorrelationStateDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$correlatedAsToFlowNodeInstanceIds = getCorrelatedAsToFlowNodeInstanceIds();
    final Object other$correlatedAsToFlowNodeInstanceIds =
        other.getCorrelatedAsToFlowNodeInstanceIds();
    if (this$correlatedAsToFlowNodeInstanceIds == null
        ? other$correlatedAsToFlowNodeInstanceIds != null
        : !this$correlatedAsToFlowNodeInstanceIds.equals(other$correlatedAsToFlowNodeInstanceIds)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventCorrelationStateDto(correlatedAsToFlowNodeInstanceIds="
        + getCorrelatedAsToFlowNodeInstanceIds()
        + ")";
  }

  public static final class Fields {

    public static final String correlatedAsToFlowNodeInstanceIds =
        "correlatedAsToFlowNodeInstanceIds";
  }
}
