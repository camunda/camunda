/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.writer.DecisionWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DecisionWriterIT extends OperateSearchAbstractIT {

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private DecisionWriter decisionWriter;

  @Test
  public void shouldDeleteDecisionRequirements() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionRequirementsIndex.getFullQualifiedName(),
        new DecisionRequirementsEntity()
            .setId("2251799813685249")
            .setKey(2251799813685249L)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setName("Invoice Business Decisions")
            .setVersion(1)
            .setResourceName("invoiceBusinessDecisions_v_1.dmn")
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionRequirements(2251799813685249L);

    assertThat(deleted).isEqualTo(1);
  }

  @Test
  public void shouldDeleteDecisionDefinitions() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionIndex.getFullQualifiedName(),
        new DecisionDefinitionEntity()
            .setId("2251799813685250")
            .setKey(2251799813685250L)
            .setDecisionId("invoiceAssignApprover")
            .setName("Assign Approver Group")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2251799813685249L)
            .setTenantId(DEFAULT_TENANT_ID));
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionIndex.getFullQualifiedName(),
        new DecisionDefinitionEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setDecisionId("invoiceClassification")
            .setName("Invoice Classification")
            .setVersion(1)
            .setDecisionRequirementsId("invoiceBusinessDecisions")
            .setDecisionRequirementsKey(2251799813685249L)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionDefinitionsFor(2251799813685249L);
    // then
    assertThat(deleted).isEqualTo(2);
  }

  @Test
  public void shouldDeleteDecisionInstances() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceTemplate.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-1")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(OffsetDateTime.now())
            .setProcessDefinitionKey(2251799813685253L)
            .setDecisionRequirementsKey(2251799813685249L)
            .setProcessInstanceKey(2251799813685255L));
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceTemplate.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-2")
            .setKey(2251799813685262L)
            .setState(io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED)
            .setEvaluationDate(OffsetDateTime.now())
            .setDecisionRequirementsKey(2251799813685249L)
            .setProcessDefinitionKey(2251799813685253L)
            .setProcessInstanceKey(2251799813685255L));

    searchContainerManager.refreshIndices("*operate-decision*");

    long deleted = decisionWriter.deleteDecisionInstancesFor(2251799813685249L);
    // then
    assertThat(deleted).isEqualTo(2);
  }

  @Test
  public void shouldNotDeleteWhenNothingFound() throws IOException {
    long decisionRequirementsKey = 123L;
    // when
    long deleted1 = decisionWriter.deleteDecisionRequirements(decisionRequirementsKey);
    long deleted2 = decisionWriter.deleteDecisionDefinitionsFor(decisionRequirementsKey);
    long deleted3 = decisionWriter.deleteDecisionInstancesFor(decisionRequirementsKey);
    // then
    assertThat(deleted1).isZero();
    assertThat(deleted2).isZero();
    assertThat(deleted3).isZero();
  }
}
