/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.globallistener;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.List;
import java.util.Objects;

public final class GlobalListenerEntity implements ExporterEntity<GlobalListenerEntity> {

  /**
   * Unique identifier of the global listener entity in the exporter store. It is mapped from the
   * combination of {@link GlobalListenerRecordValue#getId()} and {@link
   * GlobalListenerRecordValue#getListenerType()}
   */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String id;

  /**
   * The global listener identifier, unique within the cluster when combined with the {@link
   * #listenerType}. It is mapped from {@link GlobalListenerRecordValue#getId()}.
   */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String listenerId;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String type;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Integer retries;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private List<String> eventTypes;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private boolean afterNonGlobal;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Integer priority;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private GlobalListenerSource source;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private GlobalListenerType listenerType;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public GlobalListenerEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getListenerId() {
    return listenerId;
  }

  public GlobalListenerEntity setListenerId(final String listenerId) {
    this.listenerId = listenerId;
    return this;
  }

  public String getType() {
    return type;
  }

  public GlobalListenerEntity setType(final String type) {
    this.type = type;
    return this;
  }

  public int getRetries() {
    return retries;
  }

  public GlobalListenerEntity setRetries(final int retries) {
    this.retries = retries;
    return this;
  }

  public List<String> getEventTypes() {
    return eventTypes;
  }

  public GlobalListenerEntity setEventTypes(final List<String> eventTypes) {
    this.eventTypes = eventTypes;
    return this;
  }

  public boolean isAfterNonGlobal() {
    return afterNonGlobal;
  }

  public GlobalListenerEntity setAfterNonGlobal(final boolean afterNonGlobal) {
    this.afterNonGlobal = afterNonGlobal;
    return this;
  }

  public int getPriority() {
    return priority;
  }

  public GlobalListenerEntity setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  public GlobalListenerSource getSource() {
    return source;
  }

  public GlobalListenerEntity setSource(final GlobalListenerSource source) {
    this.source = source;
    return this;
  }

  public GlobalListenerType getListenerType() {
    return listenerType;
  }

  public GlobalListenerEntity setListenerType(final GlobalListenerType listenerType) {
    this.listenerType = listenerType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, listenerId, type, retries, eventTypes, afterNonGlobal, priority, source, listenerType);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GlobalListenerEntity that = (GlobalListenerEntity) o;
    return Objects.equals(retries, that.retries)
        && afterNonGlobal == that.afterNonGlobal
        && Objects.equals(priority, that.priority)
        && Objects.equals(id, that.id)
        && Objects.equals(listenerId, that.listenerId)
        && Objects.equals(type, that.type)
        && Objects.equals(eventTypes, that.eventTypes)
        && source == that.source
        && listenerType == that.listenerType;
  }

  @Override
  public String toString() {
    return "GlobalListenerEntity["
        + "id="
        + id
        + ", listenerId='"
        + listenerId
        + '\''
        + ", type='"
        + type
        + '\''
        + ", retries="
        + retries
        + ", eventTypes="
        + eventTypes
        + ", afterNonGlobal="
        + afterNonGlobal
        + ", priority="
        + priority
        + ", source="
        + source
        + ", listenerType="
        + listenerType
        + ']';
  }
}
