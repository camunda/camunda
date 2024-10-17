/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskDto {

  private String id;
  private OffsetDateTime created;
  private String processInstanceId;

  public TaskDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public OffsetDateTime getCreated() {
    return created;
  }

  public void setCreated(final OffsetDateTime created) {
    this.created = created;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof TaskDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "TaskDto(id="
        + getId()
        + ", created="
        + getCreated()
        + ", processInstanceId="
        + getProcessInstanceId()
        + ")";
  }
}
