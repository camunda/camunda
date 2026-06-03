/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.waitstate;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;

/** Represents a single waiting-state element instance stored in the wait-state index. */
public class WaitStateEntity extends AbstractExporterEntity<WaitStateEntity> {

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long processInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long elementInstanceKey;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String elementId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String elementType;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String waitStateType;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String details;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.10.0", requireDefault = false)
  private Long partitionId;

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public WaitStateEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public WaitStateEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public WaitStateEntity setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public WaitStateEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getElementType() {
    return elementType;
  }

  public WaitStateEntity setElementType(final String elementType) {
    this.elementType = elementType;
    return this;
  }

  public String getWaitStateType() {
    return waitStateType;
  }

  public WaitStateEntity setWaitStateType(final String waitStateType) {
    this.waitStateType = waitStateType;
    return this;
  }

  public String getDetails() {
    return details;
  }

  public WaitStateEntity setDetails(final String details) {
    this.details = details;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public WaitStateEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getPartitionId() {
    return partitionId;
  }

  public WaitStateEntity setPartitionId(final Long partitionId) {
    this.partitionId = partitionId;
    return this;
  }
}
