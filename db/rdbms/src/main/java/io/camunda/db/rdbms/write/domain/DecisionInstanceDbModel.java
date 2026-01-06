/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record DecisionInstanceDbModel(
    String decisionInstanceId,
    Long decisionInstanceKey,
    DecisionInstanceState state,
    OffsetDateTime evaluationDate,
    String evaluationFailure,
    String evaluationFailureMessage,
    String result,
    Long flowNodeInstanceKey,
    String flowNodeId,
    Long processInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Long decisionDefinitionKey,
    String decisionDefinitionId,
    Long decisionRequirementsKey,
    String decisionRequirementsId,
    Long rootDecisionDefinitionKey,
    DecisionDefinitionType decisionType,
    String tenantId,
    int partitionId,
    List<EvaluatedInput> evaluatedInputs,
    List<EvaluatedOutput> evaluatedOutputs,
    OffsetDateTime historyCleanupDate) {

  public static final Logger LOG = LoggerFactory.getLogger(DecisionInstanceDbModel.class);

  public static DecisionInstanceDbModel of(
      final Function<Builder, ObjectBuilder<DecisionInstanceDbModel>> fn) {
    return fn.apply(new Builder()).build();
  }

  public DecisionInstanceDbModel truncateErrorMessage(
      final int sizeLimit, final Integer byteLimit) {
    if (evaluationFailureMessage == null) {
      return this;
    }

    final var truncatedValue =
        TruncateUtil.truncateValue(evaluationFailureMessage, sizeLimit, byteLimit);

    if (truncatedValue.length() < evaluationFailureMessage.length()) {
      LOG.warn(
          "Truncated evaluation failure message for decision instance {}, original message was: {}",
          decisionInstanceKey,
          evaluationFailureMessage);
    }

    return new DecisionInstanceDbModel(
        decisionInstanceId,
        decisionInstanceKey,
        state,
        evaluationDate,
        evaluationFailure,
        truncatedValue,
        result,
        flowNodeInstanceKey,
        flowNodeId,
        processInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        decisionDefinitionKey,
        decisionDefinitionId,
        decisionRequirementsKey,
        decisionRequirementsId,
        rootDecisionDefinitionKey,
        decisionType,
        tenantId,
        partitionId,
        evaluatedInputs,
        evaluatedOutputs,
        historyCleanupDate);
  }

  public static final class Builder implements ObjectBuilder<DecisionInstanceDbModel> {

    private String decisionInstanceId;
    private Long decisionInstanceKey;
    private DecisionInstanceState state;
    private OffsetDateTime evaluationDate;
    private String evaluationFailure;
    private String evaluationFailureMessage;
    private String result;
    private Long flowNodeInstanceKey;
    private String flowNodeId;
    private Long processInstanceKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private Long decisionDefinitionKey;
    private String decisionDefinitionId;
    private Long decisionRequirementsKey;
    private String decisionRequirementsId;
    private Long rootDecisionDefinitionKey;
    private DecisionDefinitionType decisionType;
    private String tenantId;
    private int partitionId;
    private List<EvaluatedInput> evaluatedInputs;
    private List<EvaluatedOutput> evaluatedOutputs;
    private OffsetDateTime historyCleanupDate;

    public Builder decisionInstanceId(final String value) {
      decisionInstanceId = value;
      return this;
    }

    public Builder decisionInstanceKey(final Long value) {
      decisionInstanceKey = value;
      return this;
    }

    public Builder state(final DecisionInstanceState value) {
      state = value;
      return this;
    }

    public Builder evaluationDate(final OffsetDateTime value) {
      evaluationDate = value;
      return this;
    }

    public Builder evaluationFailure(final String value) {
      evaluationFailure = value;
      return this;
    }

    public Builder evaluationFailureMessage(final String value) {
      evaluationFailureMessage = value;
      return this;
    }

    public Builder result(final String value) {
      result = value;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long value) {
      flowNodeInstanceKey = value;
      return this;
    }

    public Builder flowNodeId(final String value) {
      flowNodeId = value;
      return this;
    }

    public Builder processInstanceKey(final Long value) {
      processInstanceKey = value;
      return this;
    }

    public Builder processDefinitionKey(final Long value) {
      processDefinitionKey = value;
      return this;
    }

    public Builder processDefinitionId(final String value) {
      processDefinitionId = value;
      return this;
    }

    public Builder decisionDefinitionKey(final Long value) {
      decisionDefinitionKey = value;
      return this;
    }

    public Builder decisionDefinitionId(final String value) {
      decisionDefinitionId = value;
      return this;
    }

    public Builder decisionRequirementsKey(final Long value) {
      decisionRequirementsKey = value;
      return this;
    }

    public Builder decisionRequirementsId(final String value) {
      decisionRequirementsId = value;
      return this;
    }

    public Builder rootDecisionDefinitionKey(final Long value) {
      rootDecisionDefinitionKey = value;
      return this;
    }

    public Builder decisionType(final DecisionDefinitionType value) {
      decisionType = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder partitionId(final int value) {
      partitionId = value;
      return this;
    }

    public Builder evaluatedInputs(final List<EvaluatedInput> value) {
      evaluatedInputs = value;
      return this;
    }

    public Builder evaluatedOutputs(final List<EvaluatedOutput> value) {
      evaluatedOutputs = value;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    @Override
    public DecisionInstanceDbModel build() {
      return new DecisionInstanceDbModel(
          decisionInstanceId,
          decisionInstanceKey,
          state,
          evaluationDate,
          evaluationFailure,
          evaluationFailureMessage,
          result,
          flowNodeInstanceKey,
          flowNodeId,
          processInstanceKey,
          processDefinitionKey,
          processDefinitionId,
          decisionDefinitionKey,
          decisionDefinitionId,
          decisionRequirementsKey,
          decisionRequirementsId,
          rootDecisionDefinitionKey,
          decisionType,
          tenantId,
          partitionId,
          evaluatedInputs,
          evaluatedOutputs,
          historyCleanupDate);
    }
  }

  public record EvaluatedInput(String decisionInstanceId, String id, String name, String value) {}

  public record EvaluatedOutput(
      String decisionInstanceId,
      String id,
      String name,
      String value,
      String ruleId,
      Integer ruleIndex) {}
}
