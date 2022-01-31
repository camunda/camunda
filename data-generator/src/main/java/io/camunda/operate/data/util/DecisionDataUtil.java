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
import io.camunda.operate.entities.dmn.DesicionInstanceInputEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.PayloadUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionDataUtil {

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
