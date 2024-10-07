/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.fixtures;

import io.camunda.db.rdbms.domain.ProcessInstanceDbModel;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;

public class ProcessInstanceDbModelBuilder {
    private Long processInstanceKey;
    private String bpmnProcessId;
    private Long processDefinitionKey;
    private ProcessInstanceState state;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private String tenantId;
    private Long parentProcessInstanceKey;
    private Long parentElementInstanceKey;
    private String elementId;
    private int version;

    // Public constructor to initialize the builder
    public ProcessInstanceDbModelBuilder() {
    }

    // Builder methods for each field
    public ProcessInstanceDbModelBuilder processInstanceKey(Long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
        return this;
    }

    public ProcessInstanceDbModelBuilder bpmnProcessId(String bpmnProcessId) {
        this.bpmnProcessId = bpmnProcessId;
        return this;
    }

    public ProcessInstanceDbModelBuilder processDefinitionKey(Long processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
        return this;
    }

    public ProcessInstanceDbModelBuilder state(ProcessInstanceState state) {
        this.state = state;
        return this;
    }

    public ProcessInstanceDbModelBuilder startDate(OffsetDateTime startDate) {
        this.startDate = startDate;
        return this;
    }

    public ProcessInstanceDbModelBuilder endDate(OffsetDateTime endDate) {
        this.endDate = endDate;
        return this;
    }

    public ProcessInstanceDbModelBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public ProcessInstanceDbModelBuilder parentProcessInstanceKey(Long parentProcessInstanceKey) {
        this.parentProcessInstanceKey = parentProcessInstanceKey;
        return this;
    }

    public ProcessInstanceDbModelBuilder parentElementInstanceKey(Long parentElementInstanceKey) {
        this.parentElementInstanceKey = parentElementInstanceKey;
        return this;
    }

    public ProcessInstanceDbModelBuilder elementId(String elementId) {
        this.elementId = elementId;
        return this;
    }

    public ProcessInstanceDbModelBuilder version(int version) {
        this.version = version;
        return this;
    }

    // Build method to create the record
    public ProcessInstanceDbModel build() {
        return new ProcessInstanceDbModel(
            processInstanceKey,
            bpmnProcessId,
            processDefinitionKey,
            state,
            startDate,
            endDate,
            tenantId,
            parentProcessInstanceKey,
            parentElementInstanceKey,
            elementId,
            version
        );
    }
}
