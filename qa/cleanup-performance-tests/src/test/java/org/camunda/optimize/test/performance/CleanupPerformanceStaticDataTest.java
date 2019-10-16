/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Period;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// fixed ordering for now to save import time, we first test clearing variables, then clear whole instances
@TestMethodOrder(value = MethodOrderer.Alphanumeric.class)
public class CleanupPerformanceStaticDataTest extends AbstractCleanupTest {

  @BeforeAll
  public static void setUp() {
    // given
    importData();
  }

  /**
   * Note: it tests both variables and decision instances as there is no config way to run just one of them now.
   */
  @Test
  public void aCleanupModeVariablesAndDecisionDataPerformanceTest() throws Exception {
    //given TTL of 0
    embeddedOptimizeExtensionRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeExtensionRule.getConfigurationService()
      .getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final int countProcessDefinitions = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME);
    final int processInstanceCount = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME);
    final int activityCount = elasticSearchIntegrationTestExtensionRule.getActivityCount();
    final int countDecisionDefinitions = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then no variables
    assertThat(
      "variableInstanceCount",
      getFinishedProcessInstanceVariableCount(),
      is(0)
    );
    // and no decision instances should be left in optimize
    assertThat(
      "decisionInstanceCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_INSTANCE_INDEX_NAME),
      is(0)
    );

    // and everything else is untouched
    assertThat(
      "processInstanceTypeCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME),
      is(processInstanceCount)
    );
    assertThat(
      "activityCount",
      elasticSearchIntegrationTestExtensionRule.getActivityCount(),
      is(activityCount)
    );
    assertThat(
      "processDefinitionCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME),
      is(countProcessDefinitions)
    );
    assertThat(
      "decisionDefinitionCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME),
      is(countDecisionDefinitions)
    );
  }

  @Test
  public void bCleanupModeAllPerformanceTest() throws Exception {
    //given ttl of 0
    embeddedOptimizeExtensionRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeExtensionRule.getConfigurationService().getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final int countProcessDefinitions = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME);
    final int countDecisionDefinitions = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // then no process instances, no activity and no variables should be left in optimize
    assertThat(
      "processInstanceTypeCount",
      getFinishedProcessInstanceCount(),
      is(0)
    );
    assertThat(
      "activityCount",
      getFinishedProcessInstanceActivityCount(),
      is(0)
    );
    assertThat(
      "variableInstanceCount",
      getFinishedProcessInstanceVariableCount(),
      is(0)
    );

    // and definition data is untouched
    assertThat(
      "processDefinitionCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME),
      is(countProcessDefinitions)
    );
    assertThat(
      "decisionDefinitionCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME),
      is(countDecisionDefinitions)
    );
  }

  private Integer getFinishedProcessInstanceActivityCount() {
    return elasticSearchIntegrationTestExtensionRule.getActivityCount(boolQuery().must(existsQuery(END_DATE)));
  }

  private Integer getFinishedProcessInstanceCount() {
    return elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME, boolQuery().must(existsQuery(END_DATE)));
  }

  private Integer getFinishedProcessInstanceVariableCount() {
    return elasticSearchIntegrationTestExtensionRule.getVariableInstanceCount(boolQuery().must(existsQuery(END_DATE)));
  }

}
