/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessDefinition {

  // Used for index field search and sorting
  public static final String KEY = ProcessIndex.KEY,
      NAME = ProcessIndex.NAME,
      VERSION = ProcessIndex.VERSION,
      VERSION_TAG = ProcessIndex.VERSION_TAG,
      BPMN_PROCESS_ID = ProcessIndex.BPMN_PROCESS_ID,
      TENANT_ID = ProcessIndex.TENANT_ID;

  private Long key;
  private String name;
  private Integer version;
  private String versionTag;
  private String bpmnProcessId;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public ProcessDefinition setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public ProcessDefinition setName(final String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public ProcessDefinition setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public ProcessDefinition setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessDefinition setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessDefinition setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, name, version, versionTag, bpmnProcessId, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessDefinition that = (ProcessDefinition) o;
    return Objects.equals(key, that.key)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(versionTag, that.versionTag)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "ProcessDefinition{"
        + "key="
        + key
        + ", name='"
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
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
