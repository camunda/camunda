/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceDto
    implements CreatableFromEntity<DecisionInstanceDto, DecisionInstanceEntity> {

  public static final Comparator<DecisionInstanceOutputDto>
      DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR =
          Comparator.comparingInt(DecisionInstanceOutputDto::getRuleIndex)
              .thenComparing(DecisionInstanceOutputDto::getName);
  public static final Comparator<DecisionInstanceInputDto> DECISION_INSTANCE_INPUT_DTO_COMPARATOR =
      Comparator.comparing(DecisionInstanceInputDto::getName);

  private String id;
  private DecisionInstanceStateDto state;
  private DecisionType decisionType;
  private String decisionDefinitionId;
  private String decisionId;
  private String tenantId;
  private String decisionName;
  private int decisionVersion;
  private OffsetDateTime evaluationDate;
  private String errorMessage;
  private String processInstanceId;
  private String result;
  private List<DecisionInstanceInputDto> evaluatedInputs;
  private List<DecisionInstanceOutputDto> evaluatedOutputs;

  public String getId() {
    return id;
  }

  public DecisionInstanceDto setId(final String id) {
    this.id = id;
    return this;
  }

  public DecisionInstanceStateDto getState() {
    return state;
  }

  public DecisionInstanceDto setState(final DecisionInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstanceDto setDecisionType(final DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceDto setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstanceDto setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceDto setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceDto setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceDto setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public DecisionInstanceDto setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstanceDto setResult(final String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInputDto> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceDto setEvaluatedInputs(
      final List<DecisionInstanceInputDto> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutputDto> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceDto setEvaluatedOutputs(
      final List<DecisionInstanceOutputDto> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  @Override
  public DecisionInstanceDto fillFrom(final DecisionInstanceEntity entity) {
    final List<DecisionInstanceInputDto> inputs =
        DtoCreator.create(entity.getEvaluatedInputs(), DecisionInstanceInputDto.class);
    Collections.sort(inputs, DECISION_INSTANCE_INPUT_DTO_COMPARATOR);

    final List<DecisionInstanceOutputDto> outputs =
        DtoCreator.create(entity.getEvaluatedOutputs(), DecisionInstanceOutputDto.class);
    Collections.sort(outputs, DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR);

    setId(entity.getId())
        .setDecisionDefinitionId(entity.getDecisionDefinitionId())
        .setDecisionId(entity.getDecisionId())
        .setTenantId(entity.getTenantId())
        .setDecisionName(entity.getDecisionName())
        .setDecisionType(entity.getDecisionType())
        .setDecisionVersion(entity.getDecisionVersion())
        .setErrorMessage(entity.getEvaluationFailure())
        .setEvaluationDate(entity.getEvaluationDate())
        .setEvaluatedInputs(inputs)
        .setEvaluatedOutputs(outputs)
        .setProcessInstanceId(String.valueOf(entity.getProcessInstanceKey()))
        .setResult(entity.getResult())
        .setState(DecisionInstanceStateDto.getState(entity.getState()));
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        state,
        decisionType,
        decisionDefinitionId,
        decisionId,
        tenantId,
        decisionName,
        decisionVersion,
        evaluationDate,
        errorMessage,
        processInstanceId,
        result,
        evaluatedInputs,
        evaluatedOutputs);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceDto that = (DecisionInstanceDto) o;
    return decisionVersion == that.decisionVersion
        && Objects.equals(id, that.id)
        && state == that.state
        && decisionType == that.decisionType
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs);
  }
}
