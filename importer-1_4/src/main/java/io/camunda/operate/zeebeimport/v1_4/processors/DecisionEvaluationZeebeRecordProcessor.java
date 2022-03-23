/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.zeebeimport.v1_4.record.value.DecisionEvaluationRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_4.record.value.EvaluatedDecisionValueImpl;
import io.camunda.operate.zeebeimport.v1_4.record.value.EvaluatedInputValueImpl;
import io.camunda.operate.zeebeimport.v1_4.record.value.MatchedRuleValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionEvaluationZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(
      DecisionEvaluationZeebeRecordProcessor.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  public void processDecisionEvaluationRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {
    final DecisionEvaluationRecordValueImpl decision = (DecisionEvaluationRecordValueImpl)record.getValue();
    persistDecisionInstance(record, decision, bulkRequest);
  }

  private void persistDecisionInstance(final Record record,
      final DecisionEvaluationRecordValueImpl decisionEvaluation, final BulkRequest bulkRequest)
      throws PersistenceException {
    final List<DecisionInstanceEntity> decisionEntities = createEntities(record,
        decisionEvaluation);
    logger.debug("Decision evaluation: key {}, decisionId {}", record.getKey(),
        decisionEvaluation.getDecisionId());

    try {
      for (DecisionInstanceEntity entity : decisionEntities) {
        bulkRequest.add(new IndexRequest(decisionInstanceTemplate.getFullQualifiedName())
            .id(entity.getId())
            .source(objectMapper.writeValueAsString(entity), XContentType.JSON)
        );
      }
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String
          .format("Error preparing the query to insert decision instance [%s]", record.getKey()),
          e);
    }
  }

  private List<DecisionInstanceEntity> createEntities(final Record record,
      final DecisionEvaluationRecordValueImpl decisionEvaluation) {
    List<DecisionInstanceEntity> entities = new ArrayList<>();
    for (int i = 1; i <= decisionEvaluation.getEvaluatedDecisions().size(); i++) {
      final EvaluatedDecisionValueImpl decision = decisionEvaluation
          .getEvaluatedDecisions().get(i - 1);
      OffsetDateTime timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
      final DecisionInstanceState state = getState(record, decisionEvaluation, i);
      final DecisionInstanceEntity entity = new DecisionInstanceEntity()
          .setId(getDecisionInstanceId(record.getKey(), i))
          .setKey(record.getKey())
          .setExecutionIndex(i)
          .setPosition(record.getPosition())
          .setPartitionId(record.getPartitionId())
          .setEvaluationDate(timestamp)
          .setResult(decisionEvaluation.getDecisionOutput())
          .setProcessInstanceKey(decisionEvaluation.getProcessInstanceKey())
          .setProcessDefinitionKey(decisionEvaluation.getProcessDefinitionKey())
          .setElementInstanceKey(decisionEvaluation.getElementInstanceKey())
          .setElementId(decisionEvaluation.getElementId())
          .setDecisionRequirementsKey(decisionEvaluation.getDecisionRequirementsKey())
          .setDecisionRequirementsId(decisionEvaluation.getDecisionRequirementsId())
          .setRootDecisionId(decisionEvaluation.getDecisionId())
          .setRootDecisionName(decisionEvaluation.getDecisionName())
          .setRootDecisionDefinitionId(String.valueOf(decisionEvaluation.getDecisionKey()))
          .setDecisionId(decision.getDecisionId())
          .setDecisionDefinitionId(String.valueOf(decision.getDecisionKey()))
          .setDecisionType(DecisionType.fromZeebeDecisionType(decision.getDecisionType()))
          .setDecisionName(decision.getDecisionName())
          .setDecisionVersion((int)decision.getDecisionVersion())
          .setState(state)
          .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
          .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()));
      if (state.equals(DecisionInstanceState.FAILED)) {
        entity.setEvaluationFailure(decisionEvaluation.getEvaluationFailureMessage());
      }
      entities.add(entity);
    }
    return entities;
  }

  private DecisionInstanceState getState(final Record record,
      final DecisionEvaluationRecordValueImpl decisionEvaluation, final int i) {
    if (record.getIntent().name().equals(DecisionEvaluationIntent.FAILED.name()) && i == decisionEvaluation
        .getEvaluatedDecisions().size()) {
      return DecisionInstanceState.FAILED;
    } else {
      return DecisionInstanceState.COMPLETED;
    }
  }

  private List<DecisionInstanceInputEntity> createEvaluationInputs(
      final List<EvaluatedInputValueImpl> evaluatedInputs) {
    return evaluatedInputs.stream().map(input -> new DecisionInstanceInputEntity()
        .setId(input.getInputId())
        .setName(input.getInputName())
        .setValue(input.getInputValue())).collect(Collectors.toList());
  }

  private List<DecisionInstanceOutputEntity> createEvaluationOutputs(
      final List<MatchedRuleValueImpl> matchedRules) {
    List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    matchedRules.stream().forEach(rule ->
        outputs.addAll(rule.getEvaluatedOutputs().stream().map(output ->
            new DecisionInstanceOutputEntity()
                .setRuleId(rule.getRuleId())
                .setRuleIndex(rule.getRuleIndex())
                .setId(output.getOutputId())
                .setName(output.getOutputName())
                .setValue(output.getOutputValue()))
            .collect(Collectors.toList())));
    return outputs;
  }

  private String getDecisionInstanceId(long key, int index){
    return String.format("%d-%d", key, index);
  }

}
