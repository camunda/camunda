/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.data.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.PayloadUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionDataUtil {

  public static final String DECISION_INSTANCE_ID_1 = "12121212";
  public static final String DECISION_INSTANCE_ID_2 = "13131313";

  private Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private PayloadUtil payloadUtil;

  public List<OperateEntity> createDecisionDefinitions() {
    final List<OperateEntity> decisionEntities = new ArrayList<>();

    //create DRD version 1
    decisionEntities.add(new DecisionRequirementsEntity()
        .setId("1111")
        .setKey(1111L)
        .setDecisionRequirementsId("invoiceBusinessDecisions")
        .setName("Invoice Business Decisions")
        .setVersion(1)
        .setXml(payloadUtil.readStringFromClasspath("/usertest/invoiceBusinessDecisions_v_1.dmn"))
        .setResourceName("invoiceBusinessDecisions_v_1.dmn"));
    //create Decisions
    decisionEntities.add(new DecisionDefinitionEntity()
        .setId("1222")
        .setKey(1222L)
        .setDecisionId("invoiceClassification")
        .setName("Invoice Classification")
        .setVersion(1)
        .setDecisionRequirementsId("1111")
        .setDecisionRequirementsKey(1111)
    );
    decisionEntities.add(new DecisionDefinitionEntity()
        .setId("1333")
        .setKey(1333L)
        .setDecisionId("invoice-assign-approver")
        .setName("Assign Approver Group")
        .setVersion(1)
        .setDecisionRequirementsId("1111")
        .setDecisionRequirementsKey(1111)
    );

    //create DRD version 2
    decisionEntities.add(new DecisionRequirementsEntity()
        .setId("2222")
        .setKey(2222L)
        .setDecisionRequirementsId("invoiceBusinessDecisions")
        .setName("Invoice Business Decisions")
        .setVersion(2)
        .setXml(payloadUtil.readStringFromClasspath("/usertest/invoiceBusinessDecisions_v_2.dmn"))
        .setResourceName("invoiceBusinessDecisions_v_2.dmn"));
    //create Decisions
    decisionEntities.add(new DecisionDefinitionEntity()
        .setId("2222")
        .setKey(2222L)
        .setDecisionId("invoiceClassification")
        .setName("Invoice Classification")
        .setVersion(2)
        .setDecisionRequirementsId("2222")
        .setDecisionRequirementsKey(2222)
    );
    decisionEntities.add(new DecisionDefinitionEntity()
        .setId("2333")
        .setKey(2333L)
        .setDecisionId("invoice-assign-approver")
        .setName("Assign Approver Group")
        .setVersion(2)
        .setDecisionRequirementsId("2222")
        .setDecisionRequirementsKey(2222)
    );

    return decisionEntities;
  }

  public List<DecisionInstanceEntity> createDecisionInstances() {
    List<DecisionInstanceEntity> result = new ArrayList<>();

    final List<DecisionInstanceInputEntity> inputs = new ArrayList<>();
    inputs.add(new DecisionInstanceInputEntity()
        .setId("InputClause_0og2hn3")
        .setName("Invoice Classification")
        .setValue("day-to-day expense")
    );
    inputs.add(new DecisionInstanceInputEntity()
        .setId("InputClause_0og2hn3")
        .setName("Invoice Classification")
        .setValue("budget")
    );
    final List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    outputs.add(new DecisionInstanceOutputEntity()
        .setId("OutputClause_1cthd0w")
        .setName("Approver Group")
        .setValue("budget")
        .setRuleIndex(2)
        .setRuleId("row-49839158-5")
    );
    outputs.add(new DecisionInstanceOutputEntity()
        .setId("OutputClause_1cthd0w")
        .setName("Approver Group")
        .setValue("sales")
        .setRuleIndex(1)
        .setRuleId("row-49839158-6")
    );
    outputs.add(new DecisionInstanceOutputEntity()
        .setId("OutputClause_1cthd0w")
        .setName("Approver Group")
        .setValue("accounting")
        .setRuleIndex(1)
        .setRuleId("row-49839158-1")
    );
    result.add(new DecisionInstanceEntity()
        .setId(DECISION_INSTANCE_ID_1)
        .setState(DecisionInstanceState.COMPLETED)
        .setDecisionName("Assign Approver Group")
        .setDecisionType(DecisionType.TABLE)
        .setEvaluationTime(OffsetDateTime.now())
        .setDecisionDefinitionId("1333")
        .setDecisionId("invoice-assign-approver")
        .setDecisionRequirementsId("1111")
        .setDecisionRequirementsKey(1111)
        .setElementId("taskA")
        .setElementInstanceKey(76543)
        .setEvaluatedInputs(inputs)
        .setEvaluatedOutputs(outputs)
        .setPosition(1000L)
        .setProcessDefinitionKey(35467)
        .setProcessInstanceKey(876423)
        .setResult("{\"total\": 100.0}")
    );
    result.add(new DecisionInstanceEntity()
        .setId(DECISION_INSTANCE_ID_2)
        .setState(DecisionInstanceState.FAILED)
        .setDecisionName("Assign Approver Group")
        .setDecisionType(DecisionType.TABLE)
        .setEvaluationTime(OffsetDateTime.now())
        .setEvaluationFailure("Variable not found: invoiceClassification")
        .setDecisionDefinitionId("1333")
        .setDecisionId("invoice-assign-approver")
        .setDecisionRequirementsId("1111")
        .setDecisionRequirementsKey(1111)
        .setElementId("taskA")
        .setElementInstanceKey(547547)
        .setPosition(1005L)
        .setProcessDefinitionKey(234545)
        .setProcessInstanceKey(567386)
    );
    return result;
  }

  public void persistOperateEntities(List<? extends OperateEntity> operateEntities)
      throws PersistenceException {
    try {
      BulkRequest bulkRequest = new BulkRequest();
      for (OperateEntity entity : operateEntities) {
        final String alias = getEntityToESAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }
        final IndexRequest indexRequest = new IndexRequest(alias)
            .id(entity.getId())
            .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
        bulkRequest.add(indexRequest);
      }
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }

  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap(){
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(DecisionInstanceEntity.class, decisionInstanceTemplate.getFullQualifiedName());
      entityToESAliasMap.put(DecisionRequirementsEntity.class, decisionRequirementsIndex.getFullQualifiedName());
      entityToESAliasMap.put(DecisionDefinitionEntity.class, decisionIndex.getFullQualifiedName());
    }
    return entityToESAliasMap;
  }

}
