/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class SequenceFlowEntity implements ExporterEntity<SequenceFlowEntity>, TenantOwned {

  @BeforeVersion880 private String id;

  @BeforeVersion880 private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private Long processDefinitionKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  @BeforeVersion880 private String bpmnProcessId;

  @BeforeVersion880 private String activityId;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public SequenceFlowEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SequenceFlowEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public SequenceFlowEntity setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public SequenceFlowEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public SequenceFlowEntity setActivityId(final String activityId) {
    this.activityId = activityId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public SequenceFlowEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public SequenceFlowEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        processInstanceKey,
        processDefinitionKey,
        bpmnProcessId,
        activityId,
        tenantId,
        rootProcessInstanceKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SequenceFlowEntity that = (SequenceFlowEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(activityId, that.activityId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey);
  }
}
