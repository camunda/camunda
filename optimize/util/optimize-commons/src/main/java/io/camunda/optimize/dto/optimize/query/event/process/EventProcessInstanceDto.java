/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventProcessInstanceDto extends ProcessInstanceDto {

  private List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates = new ArrayList<>();
  private Map<String, EventCorrelationStateDto> correlatedEventsById = new HashMap<>();

  protected EventProcessInstanceDto() {
  }

  protected EventProcessInstanceDto(final EventProcessInstanceDtoBuilder<?, ?> b) {
    super(b);
    if (b.pendingFlowNodeInstanceUpdates$set) {
      pendingFlowNodeInstanceUpdates = b.pendingFlowNodeInstanceUpdates$value;
    } else {
      pendingFlowNodeInstanceUpdates = $default$pendingFlowNodeInstanceUpdates();
    }
    if (b.correlatedEventsById$set) {
      correlatedEventsById = b.correlatedEventsById$value;
    } else {
      correlatedEventsById = $default$correlatedEventsById();
    }
  }

  public List<FlowNodeInstanceUpdateDto> getPendingFlowNodeInstanceUpdates() {
    return pendingFlowNodeInstanceUpdates;
  }

  public void setPendingFlowNodeInstanceUpdates(
      final List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates) {
    this.pendingFlowNodeInstanceUpdates = pendingFlowNodeInstanceUpdates;
  }

  public Map<String, EventCorrelationStateDto> getCorrelatedEventsById() {
    return correlatedEventsById;
  }

  public void setCorrelatedEventsById(
      final Map<String, EventCorrelationStateDto> correlatedEventsById) {
    this.correlatedEventsById = correlatedEventsById;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof EventProcessInstanceDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $pendingFlowNodeInstanceUpdates = getPendingFlowNodeInstanceUpdates();
    result = result * PRIME + ($pendingFlowNodeInstanceUpdates == null ? 43
        : $pendingFlowNodeInstanceUpdates.hashCode());
    final Object $correlatedEventsById = getCorrelatedEventsById();
    result =
        result * PRIME + ($correlatedEventsById == null ? 43 : $correlatedEventsById.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventProcessInstanceDto)) {
      return false;
    }
    final EventProcessInstanceDto other = (EventProcessInstanceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$pendingFlowNodeInstanceUpdates = getPendingFlowNodeInstanceUpdates();
    final Object other$pendingFlowNodeInstanceUpdates = other.getPendingFlowNodeInstanceUpdates();
    if (this$pendingFlowNodeInstanceUpdates == null ? other$pendingFlowNodeInstanceUpdates != null
        : !this$pendingFlowNodeInstanceUpdates.equals(other$pendingFlowNodeInstanceUpdates)) {
      return false;
    }
    final Object this$correlatedEventsById = getCorrelatedEventsById();
    final Object other$correlatedEventsById = other.getCorrelatedEventsById();
    if (this$correlatedEventsById == null ? other$correlatedEventsById != null
        : !this$correlatedEventsById.equals(other$correlatedEventsById)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventProcessInstanceDto(pendingFlowNodeInstanceUpdates="
        + getPendingFlowNodeInstanceUpdates() + ", correlatedEventsById="
        + getCorrelatedEventsById() + ")";
  }

  private static List<FlowNodeInstanceUpdateDto> $default$pendingFlowNodeInstanceUpdates() {
    return new ArrayList<>();
  }

  private static Map<String, EventCorrelationStateDto> $default$correlatedEventsById() {
    return new HashMap<>();
  }

  public static EventProcessInstanceDtoBuilder<?, ?> eventProcessInstanceBuilder() {
    return new EventProcessInstanceDtoBuilderImpl();
  }

  public static final class Fields {

    public static final String pendingFlowNodeInstanceUpdates = "pendingFlowNodeInstanceUpdates";
    public static final String correlatedEventsById = "correlatedEventsById";
  }

  public static abstract class EventProcessInstanceDtoBuilder<C extends EventProcessInstanceDto, B extends EventProcessInstanceDtoBuilder<C, B>> extends
      ProcessInstanceDtoBuilder<C, B> {

    private List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates$value;
    private boolean pendingFlowNodeInstanceUpdates$set;
    private Map<String, EventCorrelationStateDto> correlatedEventsById$value;
    private boolean correlatedEventsById$set;

    public B pendingFlowNodeInstanceUpdates(
        final List<FlowNodeInstanceUpdateDto> pendingFlowNodeInstanceUpdates) {
      pendingFlowNodeInstanceUpdates$value = pendingFlowNodeInstanceUpdates;
      pendingFlowNodeInstanceUpdates$set = true;
      return self();
    }

    public B correlatedEventsById(
        final Map<String, EventCorrelationStateDto> correlatedEventsById) {
      correlatedEventsById$value = correlatedEventsById;
      correlatedEventsById$set = true;
      return self();
    }

    @Override
    protected abstract B self();

    @Override
    public abstract C build();

    @Override
    public String toString() {
      return "EventProcessInstanceDto.EventProcessInstanceDtoBuilder(super=" + super.toString()
          + ", pendingFlowNodeInstanceUpdates$value=" + pendingFlowNodeInstanceUpdates$value
          + ", correlatedEventsById$value=" + correlatedEventsById$value + ")";
    }
  }

  private static final class EventProcessInstanceDtoBuilderImpl extends
      EventProcessInstanceDtoBuilder<EventProcessInstanceDto, EventProcessInstanceDtoBuilderImpl> {

    private EventProcessInstanceDtoBuilderImpl() {
    }

    @Override
    protected EventProcessInstanceDtoBuilderImpl self() {
      return this;
    }

    @Override
    public EventProcessInstanceDto build() {
      return new EventProcessInstanceDto(this);
    }
  }
}
