/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn.list;

import io.swagger.annotations.ApiModelProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceListQueryDto {

  private List<String> decisionDefinitionIds;

  private boolean evaluated;
  private boolean failed;

  private List<String> ids;
  private String processInstanceId;

  @ApiModelProperty(value = "Evaluation date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime evaluationDateAfter;

  @ApiModelProperty(value = "Evaluation date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime evaluationDateBefore;

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

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceListQueryDto that = (DecisionInstanceListQueryDto) o;
    return evaluated == that.evaluated &&
        failed == that.failed &&
        Objects.equals(decisionDefinitionIds, that.decisionDefinitionIds) &&
        Objects.equals(ids, that.ids) &&
        Objects.equals(processInstanceId, that.processInstanceId) &&
        Objects.equals(evaluationDateAfter, that.evaluationDateAfter) &&
        Objects.equals(evaluationDateBefore, that.evaluationDateBefore);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(decisionDefinitionIds, evaluated, failed, ids, processInstanceId, evaluationDateAfter,
            evaluationDateBefore);
  }

  @Override
  public String toString() {
    return "DecisionInstanceListQueryDto{" +
        "decisionDefinitionIds=" + decisionDefinitionIds +
        ", evaluated=" + evaluated +
        ", failed=" + failed +
        ", ids=" + ids +
        ", processInstanceId='" + processInstanceId + '\'' +
        ", evaluationDateAfter=" + evaluationDateAfter +
        ", evaluationDateBefore=" + evaluationDateBefore +
        '}';
  }
}
