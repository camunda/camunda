/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DecisionZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionZeebeRecordProcessor.class);

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private DecisionIndex decisionIndex;

  public void processDecisionRecord(final Record record, final BatchRequest batchRequest)
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
    LOGGER.debug(
        "Decision: key {}, decisionId {}", decisionEntity.getKey(), decisionEntity.getDecisionId());
    batchRequest.addWithId(
        decisionIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(decisionEntity.getKey()),
        decisionEntity);
  }

  private DecisionDefinitionEntity createEntity(final DecisionRecordValue decision) {
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
