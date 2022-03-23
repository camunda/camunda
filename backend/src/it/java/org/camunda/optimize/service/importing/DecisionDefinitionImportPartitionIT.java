/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;

public class DecisionDefinitionImportPartitionIT extends AbstractImportIT {

  @Test
  public void decisionDefinitionImportBatchesThatRequirePartitioningCanBeImported() {
    // given
    // more definitions than the max ES boolQuery clause limit (1024)
    final int definitionsToDeploy = 1100;
    IntStream.range(0, definitionsToDeploy).forEach(defCount -> engineIntegrationExtension.deployDecisionDefinition());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME))
      .isEqualTo(definitionsToDeploy);
  }

}
