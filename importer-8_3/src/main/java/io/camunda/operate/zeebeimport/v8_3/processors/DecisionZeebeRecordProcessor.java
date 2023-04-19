/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_3.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import java.util.HashSet;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

  public void processDecisionRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (STATES.contains(intentStr)) {
      final DecisionRecordValue decision = (DecisionRecordValue) record.getValue();
      persistDecision(decision, bulkRequest);
    }
  }

  private void persistDecision(final DecisionRecordValue decision, final BulkRequest bulkRequest)
      throws PersistenceException {
    final DecisionDefinitionEntity decisionEntity = createEntity(decision);
    logger.debug("Decision: key {}, decisionId {}", decisionEntity.getKey(),
        decisionEntity.getDecisionId());

    try {

      bulkRequest.add(new IndexRequest(decisionIndex.getFullQualifiedName())
          .id(ConversionUtils.toStringOrNull(decisionEntity.getKey()))
          .source(objectMapper.writeValueAsString(decisionEntity), XContentType.JSON)
      );
    } catch (JsonProcessingException e) {
      throw new PersistenceException(String
          .format("Error preparing the query to insert decision [%s]", decisionEntity.getKey()), e);
    }
  }

  private DecisionDefinitionEntity createEntity(DecisionRecordValue decision) {
    return new DecisionDefinitionEntity()
        .setId(String.valueOf(decision.getDecisionKey()))
        .setKey(decision.getDecisionKey())
        .setName(decision.getDecisionName())
        .setVersion(decision.getVersion())
        .setDecisionId(decision.getDecisionId())
        .setDecisionRequirementsId(decision.getDecisionRequirementsId())
        .setDecisionRequirementsKey(decision.getDecisionRequirementsKey());
  }

}
