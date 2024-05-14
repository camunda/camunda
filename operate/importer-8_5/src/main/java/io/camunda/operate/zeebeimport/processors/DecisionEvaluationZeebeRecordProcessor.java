/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.dmn.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.util.DateUtil;
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
          entity.getId(), decisionEvaluation.getTenantId(), timestamp, batchRequest);
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
              .setDecisionType(DecisionType.fromZeebeDecisionType(decision.getDecisionType()))
              .setDecisionName(decision.getDecisionName())
              .setDecisionVersion((int) decision.getDecisionVersion())
              .setState(state)
              .setResult(decision.getDecisionOutput())
              .setEvaluatedOutputs(createEvaluationOutputs(decision.getMatchedRules()))
              .setEvaluatedInputs(createEvaluationInputs(decision.getEvaluatedInputs()))
              .setTenantId(tenantOrDefault(decisionEvaluation.getTenantId()));
      if (state.equals(DecisionInstanceState.FAILED)) {
        entity.setEvaluationFailure(decisionEvaluation.getEvaluationFailureMessage());
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
