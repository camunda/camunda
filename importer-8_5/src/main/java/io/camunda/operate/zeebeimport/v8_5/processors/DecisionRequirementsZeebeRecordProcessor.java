/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_5.processors;

import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

@Component
public class DecisionRequirementsZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(
      DecisionRequirementsZeebeRecordProcessor.class);

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  private final static Set<String> STATES = new HashSet<>();
  static {
    STATES.add(ProcessIntent.CREATED.name());
  }

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  public void processDecisionRequirementsRecord(Record record, BatchRequest batchRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (STATES.contains(intentStr)) {
      final DecisionRequirementsRecordValue decisionRequirements = (DecisionRequirementsRecordValue) record
          .getValue();
      persistDecisionRequirements(decisionRequirements, batchRequest);
    }
  }

  private void persistDecisionRequirements(final DecisionRequirementsRecordValue decision,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final DecisionRequirementsEntity decisionReqEntity = createEntity(decision);
    logger.debug("Process: key {}, decisionRequirementsId {}", decisionReqEntity.getKey(),
        decisionReqEntity.getDecisionRequirementsId());

    batchRequest.addWithId(decisionRequirementsIndex.getFullQualifiedName(), ConversionUtils.toStringOrNull(decisionReqEntity.getKey()), decisionReqEntity);
  }

  private DecisionRequirementsEntity createEntity(DecisionRequirementsRecordValue decisionRequirements) {
    byte[] byteArray = decisionRequirements.getResource();
    String dmn = new String(byteArray, CHARSET);
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
