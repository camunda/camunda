/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = false"
    })
public class DecisionInstanceDaoDefaultDateSerializationIT extends OperateSearchAbstractIT {
  private static final Long FAKE_PROCESS_DEFINITION_KEY = 2251799813685253L;
  private static final Long FAKE_PROCESS_INSTANCE_KEY = 2251799813685255L;
  private final String firstDecisionEvaluationDate = "2024-02-15T22:40:10.834+0000";
  private final String secondDecisionEvaluationDate = "2024-02-15T22:41:10.834+0000";
  private final String thirdDecisionEvaluationDate = "2024-01-15T22:40:10.834+0000";
  @Autowired private DecisionInstanceDao dao;
  @Autowired private DecisionInstanceTemplate decisionInstanceIndex;
  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String indexName = decisionInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionInstanceEntity()
            .setId("2251799813685262-1")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(firstDecisionEvaluationDate))
            .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY)
            .setDecisionId("invoiceClassification")
            .setDecisionDefinitionId("2251799813685251")
            .setDecisionName("Invoice Classification")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult("\"day-to-day expense\"")
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionInstanceEntity()
            .setId("2251799813685262-2")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(secondDecisionEvaluationDate))
            .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY)
            .setDecisionId("invoiceAssignApprover")
            .setDecisionDefinitionId("2251799813685250")
            .setDecisionName("Assign Approver Group")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult("\"day-to-day expense\"")
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new DecisionInstanceEntity()
            .setId("2251799813685262-3")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(thirdDecisionEvaluationDate))
            .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY)
            .setDecisionId("invoiceAssignApprover")
            .setDecisionDefinitionId("2251799813685252")
            .setDecisionName("Process Invoice")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult("\"day-to-day expense\"")
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");
  }

  @Test
  public void shouldFilterByEvaluationDate() {
    final Results<DecisionInstance> decisionInstanceResults =
        dao.search(
            new Query<DecisionInstance>()
                .setFilter(new DecisionInstance().setEvaluationDate(firstDecisionEvaluationDate)));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(decisionInstanceResults.getItems().get(0).getEvaluationDate())
        .isEqualTo(firstDecisionEvaluationDate);
    assertThat(decisionInstanceResults.getItems().get(0).getId()).isEqualTo("2251799813685262-1");
  }

  @Test
  public void shouldFilterByEvaluationDateWithDateMath() {
    final Results<DecisionInstance> decisionInstanceResults =
        dao.search(
            new Query<DecisionInstance>()
                .setFilter(
                    new DecisionInstance()
                        .setEvaluationDate(firstDecisionEvaluationDate + "||/d")));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2L);

    DecisionInstance checkDecision =
        decisionInstanceResults.getItems().stream()
            .filter(item -> "2251799813685262-1".equals(item.getId()))
            .findFirst()
            .orElse(null);
    assertThat(checkDecision.getEvaluationDate()).isEqualTo(firstDecisionEvaluationDate);
    assertThat(checkDecision.getId()).isEqualTo("2251799813685262-1");

    checkDecision =
        decisionInstanceResults.getItems().stream()
            .filter(item -> "2251799813685262-2".equals(item.getId()))
            .findFirst()
            .orElse(null);
    assertThat(checkDecision.getEvaluationDate()).isEqualTo(secondDecisionEvaluationDate);
    assertThat(checkDecision.getId()).isEqualTo("2251799813685262-2");
  }

  @Test
  public void shouldFormatDateWhenSearchById() {
    final DecisionInstance decisionInstance = dao.byId("2251799813685262-1");

    assertThat(decisionInstance.getEvaluationDate()).isEqualTo(firstDecisionEvaluationDate);
    assertThat(decisionInstance.getId()).isEqualTo("2251799813685262-1");
  }
}
