/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.util.DateUtil;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.MatchedRuleValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionEvaluationZeebeRecordProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DecisionEvaluationZeebeRecordProcessor.class);

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private MetricsStore metricsStore;

  public void processDecisionEvaluationRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {
    final DecisionEvaluationRecordValue decision =
        (DecisionEvaluationRecordValue) record.getValue();
    persistDecisionInstance(record, decision, batchRequest);
  }

  private void persistDecisionInstance(
      final Record record,
      final DecisionEvaluationRecordValue decisionEvaluation,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final List<DecisionInstanceEntity> decisionEntities =
        createEntities(record, decisionEvaluation);
    LOGGER.debug(
        "Decision evaluation: key {}, decisionId {}",
        record.getKey(),
        decisionEvaluation.getDecisionId());

    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    for (final DecisionInstanceEntity entity : decisionEntities) {
      batchRequest.add(decisionInstanceTemplate.getFullQualifiedName(), entity);
      metricsStore.registerDecisionInstanceCompleteEvent(
          entity.getKey(),
          decisionEvaluation.getTenantId(),
          entity.getPartitionId(),
          timestamp,
          batchRequest);
    }
  }

  private List<DecisionInstanceEntity> createEntities(
      final Record record, final DecisionEvaluationRecordValue decisionEvaluation) {
    final List<DecisionInstanceEntity> entities = new ArrayList<>();
    for (int i = 1; i <= decisionEvaluation.getEvaluatedDecisions().size(); i++) {
      final EvaluatedDecisionValue decision = decisionEvaluation.getEvaluatedDecisions().get(i - 1);
      final OffsetDateTime timestamp =
          DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
      final DecisionInstanceState state = getState(record, decisionEvaluation, i);

      final DecisionInstanceEntity entity =
          new DecisionInstanceEntity()
              .setId(record.getKey(), i)
              .setKey(record.getKey())
              .setExecutionIndex(i)
              .setPosition(record.getPosition())
              .setPartitionId(record.getPartitionId())
              .setEvaluationDate(timestamp)
              .setProcessInstanceKey(decisionEvaluation.getProcessInstanceKey())
              .setProcessDefinitionKey(decisionEvaluation.getProcessDefinitionKey())
              .setBpmnProcessId(decisionEvaluation.getBpmnProcessId())
              .setElementInstanceKey(decisionEvaluation.getElementInstanceKey())
              .setElementId(decisionEvaluation.getElementId())
              .setDecisionRequirementsKey(decisionEvaluation.getDecisionRequirementsKey())
              .setDecisionRequirementsId(decisionEvaluation.getDecisionRequirementsId())
              .setRootDecisionId(decisionEvaluation.getDecisionId())
              .setRootDecisionName(decisionEvaluation.getDecisionName())
              .setRootDecisionDefinitionId(String.valueOf(decisionEvaluation.getDecisionKey()))
              .setDecisionId(decision.getDecisionId())
              .setDecisionDefinitionId(String.valueOf(decision.getDecisionKey()))
              .setDecisionType(DecisionType.fromString(decision.getDecisionType()))
              .setDecisionName(decision.getDecisionName())
              .setDecisionVersion((int) decision.getDecisionVersion())
              .setState(state)
              .setResult(decision.getDecisionOutput())
              .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
              .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()))
              .setTenantId(tenantOrDefault(decisionEvaluation.getTenantId()));
      if (state.equals(DecisionInstanceState.FAILED)) {
        entity.setEvaluationFailureMessage(decisionEvaluation.getEvaluationFailureMessage());
      }
      entities.add(entity);
    }
    return entities;
  }

  private DecisionInstanceState getState(
      final Record record, final DecisionEvaluationRecordValue decisionEvaluation, final int i) {
    if (record.getIntent().name().equals(DecisionEvaluationIntent.FAILED.name())
        && i == decisionEvaluation.getEvaluatedDecisions().size()) {
      return DecisionInstanceState.FAILED;
    } else {
      return DecisionInstanceState.EVALUATED;
    }
  }

  private List<DecisionInstanceInputEntity> createEvaluationInputs(
      final List<EvaluatedInputValue> evaluatedInputs) {
    return evaluatedInputs.stream()
        .map(
            input ->
                new DecisionInstanceInputEntity()
                    .setId(input.getInputId())
                    .setName(input.getInputName())
                    .setValue(input.getInputValue()))
        .collect(Collectors.toList());
  }

  private List<DecisionInstanceOutputEntity> createEvaluationOutputs(
      final List<MatchedRuleValue> matchedRules) {
    final List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    matchedRules.stream()
        .forEach(
            rule ->
                outputs.addAll(
                    rule.getEvaluatedOutputs().stream()
                        .map(
                            output ->
                                new DecisionInstanceOutputEntity()
                                    .setRuleId(rule.getRuleId())
                                    .setRuleIndex(rule.getRuleIndex())
                                    .setId(output.getOutputId())
                                    .setName(output.getOutputName())
                                    .setValue(output.getOutputValue()))
                        .collect(Collectors.toList())));
    return outputs;
  }
}
