/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import org.camunda.operate.util.ConversionUtils;

public class WorkflowEntity extends OperateZeebeEntity<WorkflowEntity> {

  private String name;
  private int version;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;

  public String getName() {
    return name;
  }

  @Override
  public WorkflowEntity setId(String id) {
    super.setId(id);
    setKey(ConversionUtils.toLongOrNull(id));
    return this;
  }
  
  public WorkflowEntity setName(String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public WorkflowEntity setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public WorkflowEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getBpmnXml() {
    return bpmnXml;
  }

  public WorkflowEntity setBpmnXml(String bpmnXml) {
    this.bpmnXml = bpmnXml;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public WorkflowEntity setResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowEntity that = (WorkflowEntity) o;

    if (version != that.version)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (bpmnXml != null ? !bpmnXml.equals(that.bpmnXml) : that.bpmnXml != null)
      return false;
    return resourceName != null ? resourceName.equals(that.resourceName) : that.resourceName == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (bpmnXml != null ? bpmnXml.hashCode() : 0);
    result = 31 * result + (resourceName != null ? resourceName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "WorkflowEntity{"+ "name='" + name + '\'' + ", version=" + version + ", bpmnProcessId='" + bpmnProcessId + '\'' + ", bpmnXml='" + bpmnXml + '\''
      + ", resourceName='" + resourceName + '\'' + "} " + super.toString();
  }

}
