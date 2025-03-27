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
import io.camunda.operate.util.ConversionUtils;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionRequirementsZeebeRecordProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DecisionRequirementsZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private static final Set<String> STATES = new HashSet<>();

  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  public void processDecisionRequirementsRecord(
      final Record record, final BatchRequest batchRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (STATES.contains(intentStr)) {
      final DecisionRequirementsRecordValue decisionRequirements =
          (DecisionRequirementsRecordValue) record.getValue();
      persistDecisionRequirements(decisionRequirements, batchRequest);
    }
  }

  private void persistDecisionRequirements(
      final DecisionRequirementsRecordValue decision, final BatchRequest batchRequest)
      throws PersistenceException {
    final DecisionRequirementsEntity decisionReqEntity = createEntity(decision);
    LOGGER.debug(
        "Process: key {}, decisionRequirementsId {}",
        decisionReqEntity.getKey(),
        decisionReqEntity.getDecisionRequirementsId());

    batchRequest.addWithId(
        decisionRequirementsIndex.getFullQualifiedName(),
        ConversionUtils.toStringOrNull(decisionReqEntity.getKey()),
        decisionReqEntity);
  }

  private DecisionRequirementsEntity createEntity(
      final DecisionRequirementsRecordValue decisionRequirements) {
    final byte[] byteArray = decisionRequirements.getResource();
    final String dmn = new String(byteArray, CHARSET);
    return new DecisionRequirementsEntity()
        .setId(String.valueOf(decisionRequirements.getDecisionRequirementsKey()))
        .setKey(decisionRequirements.getDecisionRequirementsKey())
        .setName(decisionRequirements.getDecisionRequirementsName())
        .setDecisionRequirementsId(decisionRequirements.getDecisionRequirementsId())
        .setVersion(decisionRequirements.getDecisionRequirementsVersion())
        .setResourceName(decisionRequirements.getResourceName())
        .setXml(dmn)
        .setTenantId(tenantOrDefault(decisionRequirements.getTenantId()));
  }
}
