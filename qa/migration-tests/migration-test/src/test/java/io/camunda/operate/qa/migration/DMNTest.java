/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.migration.util.TestConstants.DEFAULT_TENANT_ID;
import static io.camunda.operate.qa.migration.v800.DMNDataGenerator.DECISION_COUNT;
import static io.camunda.operate.qa.migration.v800.DMNDataGenerator.PROCESS_INSTANCE_COUNT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.v800.DMNDataGenerator;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Before;
import org.junit.Test;

public class DMNTest extends AbstractMigrationTest {

  private String bpmnProcessId = DMNDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> processInstanceIds;

  @Before
  public void findProcessInstanceIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (processInstanceIds == null) {
      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Process instances list
      searchRequest
          .source()
          .query(
              joinWithAnd(
                  termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                  termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
      try {
        processInstanceIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(processInstanceIds).hasSize(PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testDecisionDefinitions() {
    List<DecisionDefinitionEntity> decisionDefinitions =
        entityReader.getEntitiesFor(decisionTemplate.getAlias(), DecisionDefinitionEntity.class);
    assertThat(decisionDefinitions).hasSize(DECISION_COUNT);
    decisionDefinitions.forEach(
        di -> {
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }

  @Test
  public void testDRD() {
    List<DecisionRequirementsEntity> decisionRequirements =
        entityReader.getEntitiesFor(
            decisionRequirementsIndex.getAlias(), DecisionRequirementsEntity.class);
    assertThat(decisionRequirements).hasSize(1);
    decisionRequirements.forEach(
        di -> {
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }

  @Test
  public void testDecisionInstances() {
    SearchRequest searchRequest = new SearchRequest(decisionInstanceTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(DecisionInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<DecisionInstanceEntity> decisionInstances =
        entityReader.searchEntitiesFor(searchRequest, DecisionInstanceEntity.class);
    assertThat(decisionInstances).hasSize(PROCESS_INSTANCE_COUNT * DECISION_COUNT);
    decisionInstances.forEach(
        di -> {
          assertThat(di.getEvaluatedInputs())
              .map(DecisionInstanceInputEntity::getValue)
              .doesNotContainNull();
          assertThat(di.getEvaluatedOutputs())
              .map(DecisionInstanceOutputEntity::getValue)
              .doesNotContainNull();
          assertThat(di.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });
  }
}
