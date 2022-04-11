/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.performance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;

@Tag("engine-cleanup")
public class DecisionCleanupPerformanceStaticDataTest extends AbstractDataCleanupTest {

  @BeforeAll
  public static void setUp() {
    embeddedOptimizeExtension.setupOptimize();
    // given
    // Note that when these tests run on jenkins, data is usually imported already during the "import" stage of the job
    importEngineData();
  }

  @Test
  public void cleanupPerformanceTest() throws Exception {
    // given ttl of 0
    getCleanupConfiguration().getDecisionCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().setTtl(Period.parse("P0D"));
    // we assert there is some data as a precondition as data is expected to be provided by the environment
    assertThat(getDecisionInstanceCount()).isPositive();
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then no decision instances should be left
    assertThat(getDecisionInstanceCount()).isZero();
  }

  private Integer getDecisionInstanceCount() {
    return elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_INSTANCE_MULTI_ALIAS);
  }

}
