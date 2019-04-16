/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.time.Period;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_DEF_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// fixed ordering for now to save import time, we first test clearing variables, then clear whole instances
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CleanupPerformanceStaticDataTest extends AbstractCleanupTest {

  @BeforeClass
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
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService()
      .getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final int countProcessDefinitions = elasticSearchRule.getDocumentCountOf(PROC_DEF_TYPE);
    final int processInstanceCount = elasticSearchRule.getDocumentCountOf(PROC_INSTANCE_TYPE);
    final int activityCount = elasticSearchRule.getActivityCount();
    final int countDecisionDefinitions = elasticSearchRule.getDocumentCountOf(DECISION_DEFINITION_TYPE);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshAllOptimizeIndices();

    // then no variables
    assertThat(
      "variableInstanceCount",
      getFinishedProcessInstanceVariableCount(),
      is(0)
    );
    // and no decision instances should be left in optimize
    assertThat(
      "decisionInstanceCount",
      elasticSearchRule.getDocumentCountOf(DECISION_INSTANCE_TYPE),
      is(0)
    );

    // and everything else is untouched
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getDocumentCountOf(PROC_INSTANCE_TYPE),
      is(processInstanceCount)
    );
    assertThat(
      "activityCount",
      elasticSearchRule.getActivityCount(),
      is(activityCount)
    );
    assertThat(
      "processDefinitionCount",
      elasticSearchRule.getDocumentCountOf(PROC_DEF_TYPE),
      is(countProcessDefinitions)
    );
    assertThat(
      "decisionDefinitionCount",
      elasticSearchRule.getDocumentCountOf(DECISION_DEFINITION_TYPE),
      is(countDecisionDefinitions)
    );
  }

  @Test
  public void bCleanupModeAllPerformanceTest() throws Exception {
    //given ttl of 0
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final int countProcessDefinitions = elasticSearchRule.getDocumentCountOf(PROC_DEF_TYPE);
    final int countDecisionDefinitions = elasticSearchRule.getDocumentCountOf(DECISION_DEFINITION_TYPE);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshAllOptimizeIndices();

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
      elasticSearchRule.getDocumentCountOf(PROC_DEF_TYPE),
      is(countProcessDefinitions)
    );
    assertThat(
      "decisionDefinitionCount",
      elasticSearchRule.getDocumentCountOf(DECISION_DEFINITION_TYPE),
      is(countDecisionDefinitions)
    );
  }

  private Integer getFinishedProcessInstanceActivityCount() {
    return elasticSearchRule.getActivityCount(boolQuery().must(existsQuery(END_DATE)));
  }

  private Integer getFinishedProcessInstanceCount() {
    return elasticSearchRule.getDocumentCountOf(PROC_INSTANCE_TYPE, boolQuery().must(existsQuery(END_DATE)));
  }

  private Integer getFinishedProcessInstanceVariableCount() {
    return elasticSearchRule.getVariableInstanceCount(boolQuery().must(existsQuery(END_DATE)));
  }

}
