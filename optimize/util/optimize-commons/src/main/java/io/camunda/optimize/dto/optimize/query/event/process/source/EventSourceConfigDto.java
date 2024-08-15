/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@SuperBuilder // TODO: Not sure how to delombok this
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CamundaEventSourceConfigDto.class),
  @JsonSubTypes.Type(value = ExternalEventSourceConfigDto.class)
})
public abstract class EventSourceConfigDto {

  @Builder.Default protected List<EventScopeType> eventScope = Arrays.asList(EventScopeType.ALL);

  public EventSourceConfigDto(final List<EventScopeType> eventScope) {
    this.eventScope = eventScope;
  }

  public EventSourceConfigDto() {}

  public List<EventScopeType> getEventScope() {
    return eventScope;
  }

  public void setEventScope(final List<EventScopeType> eventScope) {
    this.eventScope = eventScope;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EventSourceConfigDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $eventScope = getEventScope();
    result = result * PRIME + ($eventScope == null ? 43 : $eventScope.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventSourceConfigDto)) {
      return false;
    }
    final EventSourceConfigDto other = (EventSourceConfigDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$eventScope = getEventScope();
    final Object other$eventScope = other.getEventScope();
    if (this$eventScope == null
        ? other$eventScope != null
        : !this$eventScope.equals(other$eventScope)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventSourceConfigDto(eventScope=" + getEventScope() + ")";
  }

  public static final class Fields {

    public static final String eventScope = "eventScope";
  }
}
