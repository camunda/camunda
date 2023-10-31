/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

@Component
public class DecisionZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(
      DecisionZeebeRecordProcessor.class);

  private final static Set<String> STATES = new HashSet<>();
  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DecisionIndex decisionIndex;

  public void processDecisionRecord(Record record, BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (STATES.contains(intentStr)) {
      final DecisionRecordValue decision = (DecisionRecordValue) record.getValue();
      persistDecision(decision, batchRequest);
    }
  }

  private void persistDecision(final DecisionRecordValue decision, final BatchRequest batchRequest)
      throws PersistenceException {
    final DecisionDefinitionEntity decisionEntity = createEntity(decision);
    logger.debug("Decision: key {}, decisionId {}", decisionEntity.getKey(),
        decisionEntity.getDecisionId());
    batchRequest.addWithId(decisionIndex.getFullQualifiedName(), ConversionUtils.toStringOrNull(decisionEntity.getKey()), decisionEntity);
  }

  private DecisionDefinitionEntity createEntity(DecisionRecordValue decision) {
    return new DecisionDefinitionEntity()
        .setId(String.valueOf(decision.getDecisionKey()))
        .setKey(decision.getDecisionKey())
        .setName(decision.getDecisionName())
        .setVersion(decision.getVersion())
        .setDecisionId(decision.getDecisionId())
        .setDecisionRequirementsId(decision.getDecisionRequirementsId())
        .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
        .setTenantId(tenantOrDefault(decision.getTenantId()));
  }

}
