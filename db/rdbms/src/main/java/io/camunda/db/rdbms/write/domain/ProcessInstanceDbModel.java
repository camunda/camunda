/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;

public record ProcessInstanceDbModel(
    Long processInstanceKey,
    String processDefinitionId,
    Long processDefinitionKey,
    ProcessInstanceState state,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String tenantId,
    Long parentProcessInstanceKey,
    Long parentElementInstanceKey,
    String elementId,
    int version) {

  public ProcessInstanceDbModelBuilder toBuilder() {
    return new ProcessInstanceDbModelBuilder()
        .processInstanceKey(processInstanceKey)
        .processInstanceKey(processInstanceKey())
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(processDefinitionId)
        .startDate(startDate)
        .endDate(endDate)
        .parentProcessInstanceKey(parentProcessInstanceKey)
        .parentElementInstanceKey(parentElementInstanceKey)
        .state(state)
        .tenantId(tenantId)
        .version(version);
  }

  public static class ProcessInstanceDbModelBuilder {

    private Long processInstanceKey;
    private String processDefinitionId;
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
    public ProcessInstanceDbModelBuilder() {}

    // Builder methods for each field
    public ProcessInstanceDbModelBuilder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public ProcessInstanceDbModelBuilder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public ProcessInstanceDbModelBuilder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public ProcessInstanceDbModelBuilder state(final ProcessInstanceState state) {
      this.state = state;
      return this;
    }

    public ProcessInstanceDbModelBuilder startDate(final OffsetDateTime startDate) {
      this.startDate = startDate;
      return this;
    }

    public ProcessInstanceDbModelBuilder endDate(final OffsetDateTime endDate) {
      this.endDate = endDate;
      return this;
    }

    public ProcessInstanceDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public ProcessInstanceDbModelBuilder parentProcessInstanceKey(
        final Long parentProcessInstanceKey) {
      this.parentProcessInstanceKey = parentProcessInstanceKey;
      return this;
    }

    public ProcessInstanceDbModelBuilder parentElementInstanceKey(
        final Long parentElementInstanceKey) {
      this.parentElementInstanceKey = parentElementInstanceKey;
      return this;
    }

    public ProcessInstanceDbModelBuilder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public ProcessInstanceDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    // Build method to create the record
    public ProcessInstanceDbModel build() {
      return new ProcessInstanceDbModel(
          processInstanceKey,
          processDefinitionId,
          processDefinitionKey,
          state,
          startDate,
          endDate,
          tenantId,
          parentProcessInstanceKey,
          parentElementInstanceKey,
          elementId,
          version);
    }
  }
}
