/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Period;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// fixed ordering for now to save import time, we first test clearing variables, then clear whole instances
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CleanupPerformanceStaticDataTest extends AbstractCleanupTest {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractCleanupTest.class);

  @BeforeClass
  public static void setUp() {
    // given
    importData();
  }

  @Test
  public void aCleanupModeVariablesPerformanceTest() throws Exception {
    final ConfigurationService configurationService = getConfigurationService();
    //given TTL of 0
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService()
      .getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.VARIABLES);
    final int countProcessDefinitions = elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_DEF_TYPE);
    final int processInstanceCount = elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_INSTANCE_TYPE);
    final int activityCount = elasticSearchRule.getActivityCount(configurationService);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshAllOptimizeIndices();

    // then no variables should be left in optimize
    assertThat(
      "variableInstanceCount",
      elasticSearchRule.getVariableInstanceCount(configurationService),
      is(0)
    );
    // and everything else is untouched
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_INSTANCE_TYPE),
      is(processInstanceCount)
    );
    assertThat(
      "activityCount",
      elasticSearchRule.getActivityCount(configurationService),
      is(activityCount)
    );
    assertThat(
      "processDefinitionCount",
      elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_DEF_TYPE),
      is(countProcessDefinitions)
    );
  }

  @Test
  public void bCleanupModeAllPerformanceTest() throws Exception {
    final ConfigurationService configurationService = getConfigurationService();
    //given ttl of 0
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration()
      .setDefaultProcessDataCleanupMode(CleanupMode.ALL);
    final int countProcessDefinitions = elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_DEF_TYPE);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshAllOptimizeIndices();

    // then no process instances, no activity and no variables should be left in optimize
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_INSTANCE_TYPE),
      is(0)
    );
    assertThat(
      "activityCount",
      elasticSearchRule.getActivityCount(configurationService),
      is(0)
    );
    assertThat(
      "variableInstanceCount",
      elasticSearchRule.getVariableInstanceCount(configurationService),
      is(0)
    );
    // and process definition count is untouched
    assertThat(
      "processDefinitionCount",
      elasticSearchRule.getDocumentCountOf(ElasticsearchConstants.PROC_DEF_TYPE),
      is(countProcessDefinitions)
    );
  }

}
