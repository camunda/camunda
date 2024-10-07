/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.fixtures;

import io.camunda.db.rdbms.domain.ProcessDefinitionDbModel;

public class ProcessDefinitionDbModelBuilder {
    private Long processDefinitionKey;
    private String bpmnProcessId;
    private String name;
    private String tenantId;
    private String versionTag;
    private int version;

    // Public constructor to initialize the builder
    public ProcessDefinitionDbModelBuilder() {
    }

    // Builder methods for each field
    public ProcessDefinitionDbModelBuilder processDefinitionKey(Long processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
        return this;
    }

    public ProcessDefinitionDbModelBuilder bpmnProcessId(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
        return this;
    }

    public ProcessDefinitionDbModelBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ProcessDefinitionDbModelBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public ProcessDefinitionDbModelBuilder versionTag(String versionTag) {
        this.versionTag = versionTag;
        return this;
    }

    public ProcessDefinitionDbModelBuilder version(int version) {
        this.version = version;
        return this;
    }

    // Build method to create the record
    public ProcessDefinitionDbModel build() {
        return new ProcessDefinitionDbModel(
            processDefinitionKey,
            bpmnProcessId,
            name,
            tenantId,
            versionTag,
            version
        );
    }
}
