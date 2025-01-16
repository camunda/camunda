/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

import java.util.Objects;

public final class ProcessDefinitionDbModel {
  private final Long processDefinitionKey;
  private final String processDefinitionId;
  private final String resourceName;
  private final String name;
  private final String tenantId;
  private final String versionTag;
  private final int version;
  private final String bpmnXml;
  private final String formId;
  private final String legacyId;

  public ProcessDefinitionDbModel(Long processDefinitionKey,
                                  String processDefinitionId,
                                  String resourceName,
                                  String name,
                                  String tenantId,
                                  String versionTag,
                                  int version,
                                  String bpmnXml,
                                  String formId,
                                  String legacyId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionId = processDefinitionId;
    this.resourceName = resourceName;
    this.name = name;
    this.tenantId = tenantId;
    this.versionTag = versionTag;
    this.version = version;
    this.bpmnXml = bpmnXml;
    this.formId = formId;
    this.legacyId = legacyId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public String resourceName() {
    return resourceName;
  }

  public String name() {
    return name;
  }

  public String tenantId() {
    return tenantId;
  }

  public String versionTag() {
    return versionTag;
  }

  public int version() {
    return version;
  }

  public String bpmnXml() {
    return bpmnXml;
  }

  public String formId() {
    return formId;
  }

  public String legacyId() {
    return legacyId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (ProcessDefinitionDbModel) obj;
    return Objects.equals(this.processDefinitionKey, that.processDefinitionKey) && Objects.equals(this.processDefinitionId,
        that.processDefinitionId) && Objects.equals(this.resourceName, that.resourceName) && Objects.equals(this.name,
        that.name) && Objects.equals(this.tenantId, that.tenantId) && Objects.equals(this.versionTag, that.versionTag) &&
        this.version == that.version && Objects.equals(this.bpmnXml, that.bpmnXml) && Objects.equals(this.formId, that.formId) && Objects.equals(this.legacyId,
        that.legacyId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processDefinitionKey, processDefinitionId, resourceName, name, tenantId, versionTag, version,
        bpmnXml, formId, legacyId);
  }

  @Override
  public String toString() {
    return "ProcessDefinitionDbModel[" + "processDefinitionKey=" + processDefinitionKey + ", " + "processDefinitionId="
        + processDefinitionId + ", " + "resourceName=" + resourceName + ", " + "name=" + name + ", " + "tenantId="
        + tenantId + ", " + "versionTag=" + versionTag + ", " + "version=" + version + ", " + "bpmnXml=" + bpmnXml
        + ", " + "formId=" + formId + ", " + "legacyId=" + legacyId + ']';
  }

  public static class ProcessDefinitionDbModelBuilder implements ObjectBuilder<ProcessDefinitionDbModel> {

    private Long processDefinitionKey;
    private String processDefinitionId;
    private String resourceName;
    private String bpmnXml;
    private String name;
    private String tenantId;
    private String versionTag;
    private int version;
    private String formId;
    private String legacyId;

    // Public constructor to initialize the builder
    public ProcessDefinitionDbModelBuilder() {
    }

    // Builder methods for each field
    public ProcessDefinitionDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public ProcessDefinitionDbModelBuilder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public ProcessDefinitionDbModelBuilder resourceName(final String resourceName) {
      this.resourceName = resourceName;
      return this;
    }

    public ProcessDefinitionDbModelBuilder bpmnXml(final String bpmnXml) {
      this.bpmnXml = bpmnXml;
      return this;
    }

    public ProcessDefinitionDbModelBuilder formId(final String formId) {
      this.formId = formId;
      return this;
    }

    public ProcessDefinitionDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public ProcessDefinitionDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public ProcessDefinitionDbModelBuilder versionTag(final String versionTag) {
      this.versionTag = versionTag;
      return this;
    }

    public ProcessDefinitionDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public ProcessDefinitionDbModelBuilder legacyId(final String id) {
      legacyId = id;
      return this;
    }

    // Build method to create the record
    @Override
    public ProcessDefinitionDbModel build() {
      return new ProcessDefinitionDbModel(processDefinitionKey, processDefinitionId, resourceName, name, tenantId,
          versionTag, version, bpmnXml, formId, legacyId);
    }
  }
}
