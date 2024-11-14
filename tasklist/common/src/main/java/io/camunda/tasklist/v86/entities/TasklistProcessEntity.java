/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.v86.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TasklistProcessEntity extends TasklistZeebeEntity<TasklistProcessEntity> {

  private String bpmnProcessId;

  private String name;

  private Integer version;

  private boolean startedByForm;

  private String formKey;
  private String formId;
  private Boolean isFormEmbedded;
  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();
  private String bpmnXml;

  public String getName() {
    return name;
  }

  public TasklistProcessEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TasklistProcessEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public List<ProcessFlowNodeEntity> getFlowNodes() {
    return flowNodes;
  }

  public TasklistProcessEntity setFlowNodes(final List<ProcessFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public TasklistProcessEntity setVersion(final Integer version) {
    this.version = version;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TasklistProcessEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public TasklistProcessEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public TasklistProcessEntity setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public boolean isStartedByForm() {
    return startedByForm;
  }

  public TasklistProcessEntity setStartedByForm(final boolean startedByForm) {
    this.startedByForm = startedByForm;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public TasklistProcessEntity setBpmnXml(final String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        name,
        version,
        startedByForm,
        formKey,
        formId,
        isFormEmbedded,
        flowNodes,
        bpmnXml);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TasklistProcessEntity that = (TasklistProcessEntity) o;
    return startedByForm == that.startedByForm
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(formId, that.formId)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(flowNodes, that.flowNodes)
        && Objects.equals(bpmnXml, that.bpmnXml);
  }
}
