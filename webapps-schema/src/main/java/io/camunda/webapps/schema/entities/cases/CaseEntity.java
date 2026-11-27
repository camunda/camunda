/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.cases;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public final class CaseEntity implements ExporterEntity<CaseEntity> {

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String id;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private long key;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private long processInstanceKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private long processDefinitionKey;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String bpmnProcessId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String tenantId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String businessId;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private String title;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private CaseState state;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private OffsetDateTime startDate;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private OffsetDateTime endDate;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private List<String> startElementIds;

  @SinceVersion(value = "8.11.0", requireDefault = false)
  private List<CaseStageEntity> stages;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public CaseEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public CaseEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CaseEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public CaseEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public CaseEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public CaseEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getBusinessId() {
    return businessId;
  }

  public CaseEntity setBusinessId(final String businessId) {
    this.businessId = businessId;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public CaseEntity setTitle(final String title) {
    this.title = title;
    return this;
  }

  public CaseState getState() {
    return state;
  }

  public CaseEntity setState(final CaseState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public CaseEntity setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public CaseEntity setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public List<String> getStartElementIds() {
    return startElementIds;
  }

  public CaseEntity setStartElementIds(final List<String> startElementIds) {
    this.startElementIds = startElementIds;
    return this;
  }

  public List<CaseStageEntity> getStages() {
    return stages;
  }

  public CaseEntity setStages(final List<CaseStageEntity> stages) {
    this.stages = stages;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        processInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        tenantId,
        businessId,
        title,
        state,
        startDate,
        endDate,
        startElementIds,
        stages);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CaseEntity that = (CaseEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(businessId, that.businessId)
        && Objects.equals(title, that.title)
        && state == that.state
        && Objects.equals(startDate, that.startDate)
        && Objects.equals(endDate, that.endDate)
        && Objects.equals(startElementIds, that.startElementIds)
        && Objects.equals(stages, that.stages);
  }
}
