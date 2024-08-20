/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.entities.operate;

import static io.camunda.exporter.utils.ExporterUtils.DEFAULT_TENANT_ID;

import io.camunda.exporter.utils.ConversionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProcessEntity extends OperateExporterEntity<ProcessEntity> {
  private String name;
  private int version;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;
  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();
  private String tenantId = DEFAULT_TENANT_ID;

  public String getName() {
    return name;
  }

  public ProcessEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public ProcessEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public ProcessEntity setBpmnXml(final String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public ProcessEntity setResourceName(final String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public List<ProcessFlowNodeEntity> getFlowNodes() {
    if (flowNodes == null) {
      flowNodes = new ArrayList<>();
    }
    return flowNodes;
  }

  public ProcessEntity setFlowNodes(final List<ProcessFlowNodeEntity> flowNodes) {
    this.flowNodes = flowNodes;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public ProcessEntity setId(final String id) {
    super.setId(id);
    setKey(ConversionUtils.toLongOrNull(id));
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), name, version, bpmnProcessId, bpmnXml, resourceName, flowNodes, tenantId);
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
    final ProcessEntity that = (ProcessEntity) o;
    return version == that.version
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(bpmnXml, that.bpmnXml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(flowNodes, that.flowNodes)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ProcessEntity{"
        + "name='"
        + name
        + '\''
        + ", version="
        + version
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", bpmnXml='"
        + bpmnXml
        + '\''
        + ", resourceName='"
        + resourceName
        + '\''
        + ", flowNodes="
        + flowNodes
        + ", tenantId='"
        + tenantId
        + '\''
        + "} "
        + super.toString();
  }
}
