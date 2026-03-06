/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.cache;

import java.time.OffsetDateTime;

/**
 * A lightweight DTO built by the {@link ZeebeImportSlidingWindowCache} every time a
 * PROCESS_INSTANCE / ELEMENT_ACTIVATING / PROCESS event is detected in the 1-second poll window.
 *
 * <p>The {@code region} field is populated by searching the entire 10-second cache for a variable
 * record whose name is {@code "region"}.
 */
public class PreFlattenedDTO {

  private long processInstanceId;
  private String processDefinitionId;
  private long processDefinitionKey;
  private String tenant;
  private int partition;
  private OffsetDateTime startTime;
  private String region;

  public PreFlattenedDTO() {}

  public long getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(final long processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(final String tenant) {
    this.tenant = tenant;
  }

  public int getPartition() {
    return partition;
  }

  public void setPartition(final int partition) {
    this.partition = partition;
  }

  public OffsetDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(final OffsetDateTime startTime) {
    this.startTime = startTime;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  @Override
  public String toString() {
    return "PreFlattenedDTO{"
        + "processInstanceId="
        + processInstanceId
        + ", processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenant='"
        + tenant
        + '\''
        + ", partition="
        + partition
        + ", startTime="
        + startTime
        + ", region='"
        + region
        + '\''
        + '}';
  }
}
