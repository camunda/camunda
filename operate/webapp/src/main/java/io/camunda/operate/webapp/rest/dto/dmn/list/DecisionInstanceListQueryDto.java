/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceListQueryDto {

  private List<String> decisionDefinitionIds;

  private boolean evaluated;
  private boolean failed;

  private List<String> ids;
  private String processInstanceId;

  @Schema(description = "Evaluation date after (inclusive)", nullable = true)
  private OffsetDateTime evaluationDateAfter;

  @Schema(description = "Evaluation date after (inclusive)", nullable = true)
  private OffsetDateTime evaluationDateBefore;

  private String tenantId;

  public List<String> getDecisionDefinitionIds() {
    return decisionDefinitionIds;
  }

  public DecisionInstanceListQueryDto setDecisionDefinitionIds(
      final List<String> decisionDefinitionIds) {
    this.decisionDefinitionIds = decisionDefinitionIds;
    return this;
  }

  public boolean isEvaluated() {
    return evaluated;
  }

  public DecisionInstanceListQueryDto setEvaluated(final boolean evaluated) {
    this.evaluated = evaluated;
    return this;
  }

  public boolean isFailed() {
    return failed;
  }

  public DecisionInstanceListQueryDto setFailed(final boolean failed) {
    this.failed = failed;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public DecisionInstanceListQueryDto setIds(final List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceListQueryDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getEvaluationDateAfter() {
    return evaluationDateAfter;
  }

  public DecisionInstanceListQueryDto setEvaluationDateAfter(
      final OffsetDateTime evaluationDateAfter) {
    this.evaluationDateAfter = evaluationDateAfter;
    return this;
  }

  public OffsetDateTime getEvaluationDateBefore() {
    return evaluationDateBefore;
  }

  public DecisionInstanceListQueryDto setEvaluationDateBefore(
      final OffsetDateTime evaluationDateBefore) {
    this.evaluationDateBefore = evaluationDateBefore;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceListQueryDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        decisionDefinitionIds,
        evaluated,
        failed,
        ids,
        processInstanceId,
        evaluationDateAfter,
        evaluationDateBefore,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceListQueryDto that = (DecisionInstanceListQueryDto) o;
    return evaluated == that.evaluated
        && failed == that.failed
        && Objects.equals(decisionDefinitionIds, that.decisionDefinitionIds)
        && Objects.equals(ids, that.ids)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(evaluationDateAfter, that.evaluationDateAfter)
        && Objects.equals(evaluationDateBefore, that.evaluationDateBefore)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionInstanceListQueryDto{"
        + "decisionDefinitionIds="
        + decisionDefinitionIds
        + ", evaluated="
        + evaluated
        + ", failed="
        + failed
        + ", ids="
        + ids
        + ", processInstanceId='"
        + processInstanceId
        + '\''
        + ", evaluationDateAfter="
        + evaluationDateAfter
        + ", evaluationDateBefore="
        + evaluationDateBefore
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
