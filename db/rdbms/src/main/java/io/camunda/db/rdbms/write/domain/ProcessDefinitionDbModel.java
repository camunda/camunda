/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record ProcessDefinitionDbModel(
    Long processDefinitionKey,
    String processDefinitionId,
    String resourceName,
    String name,
    String tenantId,
    String versionTag,
    int version,
    String bpmnXml,
    String formId) {

  public static class ProcessDefinitionDbModelBuilder
      implements ObjectBuilder<ProcessDefinitionDbModel> {

    private Long processDefinitionKey;
    private String processDefinitionId;
    private String resourceName;
    private String bpmnXml;
    private String name;
    private String tenantId;
    private String versionTag;
    private int version;
    private String formId;

    // Public constructor to initialize the builder
    public ProcessDefinitionDbModelBuilder() {}

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

    // Build method to create the record
    @Override
    public ProcessDefinitionDbModel build() {
      return new ProcessDefinitionDbModel(
          processDefinitionKey,
          processDefinitionId,
          resourceName,
          name,
          tenantId,
          versionTag,
          version,
          bpmnXml,
          formId);
    }
  }
}
