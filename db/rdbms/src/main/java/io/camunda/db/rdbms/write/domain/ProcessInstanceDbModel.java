/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.function.Function;

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
    Integer numIncidents,
    int version,
    int partitionId,
    String treePath,
    OffsetDateTime historyCleanupDate,
    Set<String> tags)
    implements DbModel<ProcessInstanceDbModel> {

  @Override
  public ProcessInstanceDbModel copy(
      final Function<ObjectBuilder<ProcessInstanceDbModel>, ObjectBuilder<ProcessInstanceDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new ProcessInstanceDbModelBuilder()
                .processInstanceKey(processInstanceKey)
                .processInstanceKey(processInstanceKey())
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .startDate(startDate)
                .endDate(endDate)
                .parentProcessInstanceKey(parentProcessInstanceKey)
                .parentElementInstanceKey(parentElementInstanceKey)
                .state(state)
                .numIncidents(numIncidents)
                .tenantId(tenantId)
                .version(version)
                .partitionId(partitionId)
                .treePath(treePath)
                .historyCleanupDate(historyCleanupDate)
                .tags(tags))
        .build();
  }

  public static class ProcessInstanceDbModelBuilder
      implements ObjectBuilder<ProcessInstanceDbModel> {

    private Long processInstanceKey;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private ProcessInstanceState state;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate = null;
    private String tenantId;
    private Long parentProcessInstanceKey;
    private Long parentElementInstanceKey;
    private int numIncidents = 0;
    private int version;
    private int partitionId;
    private String treePath;
    private OffsetDateTime historyCleanupDate;
    private Set<String> tags;

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

    public int numIncidents() {
      return numIncidents;
    }

    public ProcessInstanceDbModelBuilder numIncidents(final int numIncidents) {
      this.numIncidents = numIncidents;
      return this;
    }

    public ProcessInstanceDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public ProcessInstanceDbModelBuilder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public ProcessInstanceDbModelBuilder treePath(final String treePath) {
      this.treePath = treePath;
      return this;
    }

    public ProcessInstanceDbModelBuilder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    public ProcessInstanceDbModelBuilder tags(final Set<String> tags) {
      this.tags = tags;
      return this;
    }

    @Override
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
          numIncidents,
          version,
          partitionId,
          treePath,
          historyCleanupDate,
          tags);
    }
  }
}
