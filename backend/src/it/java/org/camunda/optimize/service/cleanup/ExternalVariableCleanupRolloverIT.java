/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex;
import org.camunda.optimize.service.util.configuration.cleanup.ExternalVariableCleanupConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

public class ExternalVariableCleanupRolloverIT extends AbstractCleanupIT {

  @BeforeEach
  @AfterEach
  public void cleanUpExternalVariableIndices() {
    elasticSearchIntegrationTestExtension.deleteAllExternalVariableIndices();
    embeddedOptimizeExtension.getElasticSearchSchemaManager().createOrUpdateOptimizeIndex(
      embeddedOptimizeExtension.getOptimizeElasticClient(),
      new ExternalProcessVariableIndex()
    );
  }

  @Test
  public void cleanupWorksAfterRollover() {
    // given
    getExternalVariableCleanupConfiguration().setEnabled(true);
    final List<ExternalProcessVariableRequestDto> varsToCleanIndex1 = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());

    // freeze time to manipulate ingestion timestamp
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    dateFreezer().dateToFreeze(getEndTimeLessThanGlobalTtl()).freezeDateAndReturn();
    ingestionClient.ingestVariables(varsToCleanIndex1);

    // trigger rollover
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.getConfigurationService().getVariableIndexRolloverConfiguration().setMaxIndexSizeGB(0);
    embeddedOptimizeExtension.getExternalProcessVariableIndexRolloverService().triggerRollover();

    final List<ExternalProcessVariableRequestDto> varsToCleanIndex2 = IntStream.range(20, 30)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());
    final List<ExternalProcessVariableRequestDto> varsToKeepIndex2 = IntStream.range(40, 50)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());

    // freeze time to manipulate ingestion timestamp
    ingestionClient.ingestVariables(varsToCleanIndex2);
    dateFreezer().dateToFreeze(now).freezeDateAndReturn();
    ingestionClient.ingestVariables(varsToKeepIndex2);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getAllStoredExternalVariables())
      .extracting(ExternalProcessVariableDto::getVariableId)
      .containsExactlyInAnyOrderElementsOf(varsToKeepIndex2.stream()
                                             .map(ExternalProcessVariableRequestDto::getId)
                                             .collect(toList()));
  }

  private ExternalVariableCleanupConfiguration getExternalVariableCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService()
      .getCleanupServiceConfiguration()
      .getExternalVariableCleanupConfiguration();
  }

  private List<ExternalProcessVariableDto> getAllStoredExternalVariables() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableDto.class
    );
  }

}
