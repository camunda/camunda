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
import java.util.Objects;
import java.util.function.Function;

public class ProcessInstanceDbModel implements DbModel<ProcessInstanceDbModel> {
  private final Long processInstanceKey;
  private final String legacyProcessInstanceId;
  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final ProcessInstanceState state;
  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final String tenantId;
  private final Long parentProcessInstanceKey;
  private final Long parentElementInstanceKey;
  private final Integer numIncidents;
  private final String elementId;
  private final int version;
  private final Boolean hasIncident;

  public ProcessInstanceDbModel(
      Long processInstanceKey,
      String legacyProcessInstanceId,
      String processDefinitionId,
      Long processDefinitionKey,
      ProcessInstanceState state,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      String tenantId,
      Long parentProcessInstanceKey,
      Long parentElementInstanceKey,
      Integer numIncidents,
      String elementId,
      int version,
      String name,
      String versionTag,
      Boolean hasIncident) {
    this.processInstanceKey = processInstanceKey;
    this.legacyProcessInstanceId = legacyProcessInstanceId;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.state = state;
    this.startDate = startDate;
    this.endDate = endDate;
    this.tenantId = tenantId;
    this.parentProcessInstanceKey = parentProcessInstanceKey;
    this.parentElementInstanceKey = parentElementInstanceKey;
    this.numIncidents = numIncidents;
    this.elementId = elementId;
    this.version = version;
    this.hasIncident = hasIncident;
  }

  public ProcessInstanceDbModel copy(
      final Function<ObjectBuilder<ProcessInstanceDbModel>, ObjectBuilder<ProcessInstanceDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new ProcessInstanceDbModelBuilder()
                .processInstanceKey(processInstanceKey)
                .legacyProcessInstanceId(legacyProcessInstanceId)
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionId(processDefinitionId)
                .startDate(startDate)
                .endDate(endDate)
                .parentProcessInstanceKey(parentProcessInstanceKey)
                .parentElementInstanceKey(parentElementInstanceKey)
                .state(state)
                .numIncidents(numIncidents)
                .tenantId(tenantId)
                .version(version))
        .build();
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public String legacyProcessInstanceId() {
    return legacyProcessInstanceId;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceState state() {
    return state;
  }

  public OffsetDateTime startDate() {
    return startDate;
  }

  public OffsetDateTime endDate() {
    return endDate;
  }

  public String tenantId() {
    return tenantId;
  }

  public Long parentProcessInstanceKey() {
    return parentProcessInstanceKey;
  }

  public Long parentElementInstanceKey() {
    return parentElementInstanceKey;
  }

  public String elementId() {
    return elementId;
  }

  public int version() {
    return version;
  }

  public Boolean hasIncident() {
    return hasIncident;
  }

  public Integer numIncidents() {
    return numIncidents;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    final var that = (ProcessInstanceDbModel) obj;
    return Objects.equals(this.processInstanceKey, that.processInstanceKey)
        && Objects.equals(this.legacyProcessInstanceId, that.legacyProcessInstanceId)
        && Objects.equals(this.processDefinitionId, that.processDefinitionId)
        && Objects.equals(this.processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(this.state, that.state)
        && Objects.equals(this.startDate, that.startDate)
        && Objects.equals(this.endDate, that.endDate)
        && Objects.equals(this.tenantId, that.tenantId)
        && Objects.equals(this.parentProcessInstanceKey, that.parentProcessInstanceKey)
        && Objects.equals(this.parentElementInstanceKey, that.parentElementInstanceKey)
        && Objects.equals(this.elementId, that.elementId)
        && this.version == that.version;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        processInstanceKey,
        legacyProcessInstanceId,
        processDefinitionId,
        processDefinitionKey,
        state,
        startDate,
        endDate,
        tenantId,
        parentProcessInstanceKey,
        parentElementInstanceKey,
        numIncidents,
        elementId,
        version);
  }

  @Override
  public String toString() {
    return "ProcessInstanceDbModel["
        + "processInstanceKey="
        + processInstanceKey
        + ", "
        + "legacyProcessInstanceId="
        + legacyProcessInstanceId
        + ", "
        + "processDefinitionId="
        + processDefinitionId
        + ", "
        + "processDefinitionKey="
        + processDefinitionKey
        + ", "
        + "state="
        + state
        + ", "
        + "startDate="
        + startDate
        + ", "
        + "endDate="
        + endDate
        + ", "
        + "tenantId="
        + tenantId
        + ", "
        + "parentProcessInstanceKey="
        + parentProcessInstanceKey
        + ", "
        + "parentElementInstanceKey="
        + parentElementInstanceKey
        + ", "
        + "numIncidents="
        + numIncidents
        + ", "
        + "elementId="
        + elementId
        + ", "
        + "version="
        + version
        + ']';
  }

  public static class ProcessInstanceDbModelBuilder
      implements ObjectBuilder<ProcessInstanceDbModel> {

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
    private int numIncidents;
    private int version;
    private String legacyProcessInstanceId;
    private Boolean hasIncident;

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

    public ProcessInstanceDbModelBuilder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public ProcessInstanceDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public ProcessInstanceDbModelBuilder legacyProcessInstanceId(
        final String legacyProcessInstanceId) {
      this.legacyProcessInstanceId = legacyProcessInstanceId;
      return this;
    }

    public ProcessInstanceDbModelBuilder hasIncident(Boolean hasIncident) {
      this.hasIncident = hasIncident;
      return this;
    }

    @Override
    public ProcessInstanceDbModel build() {
      return new ProcessInstanceDbModel(
          processInstanceKey,
          legacyProcessInstanceId,
          processDefinitionId,
          processDefinitionKey,
          state,
          startDate,
          endDate,
          tenantId,
          parentProcessInstanceKey,
          parentElementInstanceKey,
          numIncidents,
          elementId,
          version,
          null,
          null,
          hasIncident);
    }
  }
}
