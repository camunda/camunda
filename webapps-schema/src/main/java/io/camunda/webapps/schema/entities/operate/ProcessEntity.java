/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProcessEntity implements ExporterEntity<ProcessEntity>, TenantOwned {

  private String id;
  private long key;
  private String name;
  private int version;
  private String versionTag;
  private String bpmnProcessId;
  private String bpmnXml;
  private String resourceName;
  private List<ProcessFlowNodeEntity> flowNodes = new ArrayList<>();
  @JsonIgnore private List<String> callActivityIds = new ArrayList<>();
  private String formId;
  private String formKey;
  private Boolean isFormEmbedded;
  private Boolean isPublic;
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  public String getName() {
    return name;
  }

  public ProcessEntity setName(final String name) {
    this.name = name;
    return this;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ProcessEntity setId(final String id) {
    this.id = id;
    setKey(Long.valueOf(id));
    return this;
  }

  public long getKey() {
    return key;
  }

  public ProcessEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        name,
        version,
        versionTag,
        bpmnProcessId,
        bpmnXml,
        resourceName,
        flowNodes,
        callActivityIds,
        tenantId,
        formId,
        formKey,
        isFormEmbedded,
        isPublic);
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
    return Objects.equals(id, that.id)
        && key == that.key
        && version == that.version
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(name, that.name)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(bpmnXml, that.bpmnXml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(flowNodes, that.flowNodes)
        && Objects.equals(callActivityIds, that.callActivityIds)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(formId, that.formId)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(isFormEmbedded, that.isFormEmbedded)
        && Objects.equals(isPublic, that.isPublic);
  }

  @Override
  public String toString() {
    return "ProcessEntity{"
        + "name='"
        + name
        + '\''
        + ", version="
        + version
        + '\''
        + ", versionTag="
        + versionTag
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
        + ", callActivityIds="
        + callActivityIds
        + ", formId="
        + formId
        + ", formKey="
        + formKey
        + ", isFormEmbedded="
        + isFormEmbedded
        + ", isPublic="
        + isPublic
        + ", tenantId='"
        + tenantId
        + '\''
        + "} "
        + super.toString();
  }

  public int getVersion() {
    return version;
  }

  public ProcessEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ProcessEntity setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
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

  public List<String> getCallActivityIds() {
    return callActivityIds;
  }

  public ProcessEntity setCallActivityIds(final List<String> callActivityIds) {
    this.callActivityIds = callActivityIds;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public ProcessEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getFormId() {
    return formId;
  }

  public ProcessEntity setFormId(final String formId) {
    this.formId = formId;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public ProcessEntity setFormKey(final String formKey) {
    this.formKey = formKey;
    return this;
  }

  public Boolean getIsFormEmbedded() {
    return isFormEmbedded;
  }

  public ProcessEntity setIsFormEmbedded(final Boolean isFormEmbedded) {
    this.isFormEmbedded = isFormEmbedded;
    return this;
  }

  public Boolean getIsPublic() {
    return isPublic;
  }

  public ProcessEntity setIsPublic(final Boolean isPublic) {
    this.isPublic = isPublic;
    return this;
  }
}
