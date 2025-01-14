/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceStateDto;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DecisionInstanceForListDto {

  private String id;
  private DecisionInstanceStateDto state;
  private String decisionName;
  private Integer decisionVersion;
  private OffsetDateTime evaluationDate;
  private String processInstanceId;
  private String tenantId;

  /**
   * Sort values, define the position of process instance in the list and may be used to search for
   * previous or following page.
   */
  private SortValuesWrapper[] sortValues;

  public static DecisionInstanceForListDto createFrom(
      final DecisionInstanceEntity entity, final ObjectMapper objectMapper) {
    return new DecisionInstanceForListDto()
        .setDecisionName(entity.getDecisionName())
        .setDecisionVersion(entity.getDecisionVersion())
        .setEvaluationDate(entity.getEvaluationDate())
        .setId(entity.getId())
        .setProcessInstanceId(String.valueOf(entity.getProcessInstanceKey()))
        .setState(DecisionInstanceStateDto.getState(entity.getState()))
        .setSortValues(SortValuesWrapper.createFrom(entity.getSortValues(), objectMapper))
        .setTenantId(entity.getTenantId());
  }

  public static List<DecisionInstanceForListDto> createFrom(
      final List<DecisionInstanceEntity> decisionInstanceEntities,
      final ObjectMapper objectMapper) {
    if (decisionInstanceEntities == null) {
      return new ArrayList<>();
    }
    return decisionInstanceEntities.stream()
        .filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  public String getId() {
    return id;
  }

  public DecisionInstanceForListDto setId(final String id) {
    this.id = id;
    return this;
  }

  public DecisionInstanceStateDto getState() {
    return state;
  }

  public DecisionInstanceForListDto setState(final DecisionInstanceStateDto state) {
    this.state = state;
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

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceForListDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public DecisionInstanceForListDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id, state, decisionName, decisionVersion, evaluationDate, processInstanceId, tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
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
    return Objects.equals(id, that.id)
        && state == that.state
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(decisionVersion, that.decisionVersion)
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public String toString() {
    return "DecisionInstanceForListDto{"
        + "id='"
        + id
        + '\''
        + ", state="
        + state
        + ", decisionName='"
        + decisionName
        + '\''
        + ", decisionVersion="
        + decisionVersion
        + ", evaluationDate="
        + evaluationDate
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", sortValues="
        + Arrays.toString(sortValues)
        + '}';
  }
}
