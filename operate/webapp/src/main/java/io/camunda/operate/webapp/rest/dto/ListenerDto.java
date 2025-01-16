/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.entities.listener.ListenerEventType;
import io.camunda.webapps.schema.entities.listener.ListenerState;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import io.camunda.webapps.schema.entities.JobEntity;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class ListenerDto {
  private ListenerType listenerType;
  private String listenerKey;
  private ListenerState state;
  private String jobType;
  private ListenerEventType event;
  private OffsetDateTime time;

  private SortValuesWrapper[] sortValues;

  public ListenerType getListenerType() {
    return listenerType;
  }

  public ListenerDto setListenerType(final ListenerType listenerType) {
    this.listenerType = listenerType;
    return this;
  }

  public String getListenerKey() {
    return listenerKey;
  }

  public ListenerDto setListenerKey(final String listenerKey) {
    this.listenerKey = listenerKey;
    return this;
  }

  public ListenerState getState() {
    return state;
  }

  public ListenerDto setState(final ListenerState state) {
    this.state = state;
    return this;
  }

  public String getJobType() {
    return jobType;
  }

  public ListenerDto setJobType(final String jobType) {
    this.jobType = jobType;
    return this;
  }

  public ListenerEventType getEvent() {
    return event;
  }

  public ListenerDto setEvent(final ListenerEventType event) {
    this.event = event;
    return this;
  }

  public OffsetDateTime getTime() {
    return time;
  }

  public ListenerDto setTime(final OffsetDateTime time) {
    this.time = time;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public ListenerDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public static ListenerDto fromJobEntity(final JobEntity jobEntity) {
    return new ListenerDto()
        .setListenerType(ListenerType.fromZeebeJobKind(jobEntity.getJobKind()))
        .setListenerKey(Long.toString(jobEntity.getKey()))
        .setJobType(jobEntity.getType())
        .setState(ListenerState.fromZeebeJobIntent(jobEntity.getState()))
        .setEvent(ListenerEventType.fromZeebeListenerEventType(jobEntity.getListenerEventType()))
        .setTime(jobEntity.getEndTime());
  }

  @Override
  public int hashCode() {
    return Objects.hash(listenerType, listenerKey, state, jobType, event, time);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ListenerDto that = (ListenerDto) o;
    return listenerType == that.listenerType
        && Objects.equals(listenerKey, that.listenerKey)
        && state == that.state
        && Objects.equals(jobType, that.jobType)
        && event == that.event
        && Objects.equals(time, that.time)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public String toString() {
    return "ListenerDto{"
        + "listenerType="
        + listenerType
        + ", listenerKey='"
        + listenerKey
        + '\''
        + ", state="
        + state
        + ", jobType='"
        + jobType
        + '\''
        + ", event="
        + event
        + ", time="
        + time
        + ", sortValues="
        + Arrays.toString(sortValues)
        + '}';
  }
}
