/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceStateDto;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;

public class DecisionInstanceForListDto implements
    CreatableFromEntity<DecisionInstanceForListDto, DecisionInstanceEntity> {

  private String id;
  private DecisionInstanceStateDto state;
  private String decisionName;
  private Integer decisionVersion;
  private OffsetDateTime evaluationDate;
  private String processInstanceId;

  /**
   * Sort values, define the position of process instance in the list and may be used to search
   * for previous or following page.
   */
  private String[] sortValues;

  public String getId() {
    return id;
  }

  public DecisionInstanceStateDto getState() {
    return state;
  }

  public DecisionInstanceForListDto setState(
      final DecisionInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public DecisionInstanceForListDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceForListDto setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public Integer getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceForListDto setDecisionVersion(final Integer decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceForListDto setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceForListDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public DecisionInstanceForListDto setSortValues(final String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public DecisionInstanceForListDto fillFrom(final DecisionInstanceEntity entity) {
    return this.setDecisionName(entity.getDecisionName())
        .setDecisionVersion(entity.getDecisionVersion())
        .setEvaluationDate(entity.getEvaluationDate())
        .setId(entity.getId())
        .setProcessInstanceId(String.valueOf(entity.getProcessInstanceKey()))
        .setState(DecisionInstanceStateDto.getState(entity.getState()))
        .setSortValues(Arrays.stream(entity.getSortValues())
            .map(String::valueOf)
            .toArray(String[]::new));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceForListDto that = (DecisionInstanceForListDto) o;
    return Objects.equals(id, that.id) &&
        state == that.state &&
        Objects.equals(decisionName, that.decisionName) &&
        Objects.equals(decisionVersion, that.decisionVersion) &&
        Objects.equals(evaluationDate, that.evaluationDate) &&
        Objects.equals(processInstanceId, that.processInstanceId) &&
        Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects
        .hash(id, state, decisionName, decisionVersion, evaluationDate, processInstanceId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return "DecisionInstanceForListDto{" +
        "id='" + id + '\'' +
        ", state=" + state +
        ", decisionName='" + decisionName + '\'' +
        ", decisionVersion=" + decisionVersion +
        ", evaluationDate=" + evaluationDate +
        ", processInstanceId='" + processInstanceId + '\'' +
        ", sortValues=" + Arrays.toString(sortValues) +
        '}';
  }
}
