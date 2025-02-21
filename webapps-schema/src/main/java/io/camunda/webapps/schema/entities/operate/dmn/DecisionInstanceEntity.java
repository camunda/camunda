/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate.dmn;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceEntity
    implements ExporterEntity<DecisionInstanceEntity>,
        PartitionedEntity<DecisionInstanceEntity>,
        TenantOwned {

  private String id;
  private long key;
  private int partitionId;
  private Integer executionIndex;
  private DecisionInstanceState state;
  private OffsetDateTime evaluationDate;
  private String evaluationFailure;
  private Long position;
  private long decisionRequirementsKey;
  private String decisionRequirementsId;
  private long processDefinitionKey;
  private long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private long elementInstanceKey;
  private String elementId;
  private String decisionId;
  private String decisionDefinitionId;
  private String decisionName;
  private int decisionVersion;
  private String rootDecisionName;
  private String rootDecisionId;
  private String rootDecisionDefinitionId;
  private DecisionType decisionType;
  private String result;
  private List<DecisionInstanceInputEntity> evaluatedInputs = new ArrayList<>();
  private List<DecisionInstanceOutputEntity> evaluatedOutputs = new ArrayList<>();
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @JsonIgnore private Object[] sortValues;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DecisionInstanceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public DecisionInstanceEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public DecisionInstanceEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public static Long extractKey(final String id) {
    return Long.valueOf(id.split("-")[0]);
  }

  public DecisionInstanceEntity setId(final Long key, final int executionIndex) {
    return setId(String.format("%d-%d", key, executionIndex));
  }

  public Integer getExecutionIndex() {
    return executionIndex;
  }

  public DecisionInstanceEntity setExecutionIndex(final Integer executionIndex) {
    this.executionIndex = executionIndex;
    return this;
  }

  public DecisionInstanceState getState() {
    return state;
  }

  public DecisionInstanceEntity setState(final DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceEntity setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  public DecisionInstanceEntity setEvaluationFailure(final String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public DecisionInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceEntity setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionInstanceEntity setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionInstanceEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstanceEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionInstanceEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public DecisionInstanceEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public DecisionInstanceEntity setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public DecisionInstanceEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstanceEntity setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceEntity setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceEntity setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public String getRootDecisionName() {
    return rootDecisionName;
  }

  public DecisionInstanceEntity setRootDecisionName(final String rootDecisionName) {
    this.rootDecisionName = rootDecisionName;
    return this;
  }

  public String getRootDecisionId() {
    return rootDecisionId;
  }

  public DecisionInstanceEntity setRootDecisionId(final String rootDecisionId) {
    this.rootDecisionId = rootDecisionId;
    return this;
  }

  public String getRootDecisionDefinitionId() {
    return rootDecisionDefinitionId;
  }

  public DecisionInstanceEntity setRootDecisionDefinitionId(final String rootDecisionDefinitionId) {
    this.rootDecisionDefinitionId = rootDecisionDefinitionId;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstanceEntity setDecisionType(final DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstanceEntity setResult(final String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInputEntity> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceEntity setEvaluatedInputs(
      final List<DecisionInstanceInputEntity> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutputEntity> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceEntity setEvaluatedOutputs(
      final List<DecisionInstanceOutputEntity> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public DecisionInstanceEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    int result1 =
        Objects.hash(
            id,
            key,
            partitionId,
            executionIndex,
            state,
            evaluationDate,
            evaluationFailure,
            position,
            decisionRequirementsKey,
            decisionRequirementsId,
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            elementInstanceKey,
            elementId,
            decisionId,
            decisionDefinitionId,
            decisionName,
            decisionVersion,
            rootDecisionName,
            rootDecisionId,
            rootDecisionDefinitionId,
            decisionType,
            result,
            evaluatedInputs,
            evaluatedOutputs,
            tenantId);
    result1 = 31 * result1 + Arrays.hashCode(sortValues);
    return result1;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceEntity that = (DecisionInstanceEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && partitionId == that.partitionId
        && decisionRequirementsKey == that.decisionRequirementsKey
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && decisionVersion == that.decisionVersion
        && Objects.equals(executionIndex, that.executionIndex)
        && state == that.state
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(position, that.position)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(rootDecisionName, that.rootDecisionName)
        && Objects.equals(rootDecisionId, that.rootDecisionId)
        && Objects.equals(rootDecisionDefinitionId, that.rootDecisionDefinitionId)
        && decisionType == that.decisionType
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }
}
