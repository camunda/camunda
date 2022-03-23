/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.service.util.configuration.cleanup.ExternalVariableCleanupConfiguration;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

public class ExternalVariableCleanupIT extends AbstractCleanupIT {

  @Test
  public void externalVariableCleanupWorks() {
    // given
    getExternalVariableCleanupConfiguration().setEnabled(true);
    final List<ExternalProcessVariableRequestDto> variablesToKeep = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());
    final List<ExternalProcessVariableRequestDto> variablesToClean = IntStream.range(20, 30)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());

    // freeze time to manipulate ingestion timestamp
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    ingestionClient.ingestVariables(variablesToKeep);
    dateFreezer().dateToFreeze(getEndTimeLessThanGlobalTtl()).freezeDateAndReturn();
    ingestionClient.ingestVariables(variablesToClean);
    dateFreezer().dateToFreeze(now).freezeDateAndReturn();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getAllStoredExternalVariables())
      .extracting(ExternalProcessVariableDto::getVariableId)
      .containsExactlyInAnyOrderElementsOf(variablesToKeep.stream()
                                             .map(ExternalProcessVariableRequestDto::getId)
                                             .collect(toSet()));
  }

  @Test
  public void externalVariableCleanupCanBeDisabled() {
    // given
    getExternalVariableCleanupConfiguration().setEnabled(false);
    final List<ExternalProcessVariableRequestDto> variables = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
      .collect(toList());

    // freeze time to manipulate ingestion timestamp
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    dateFreezer().dateToFreeze(getEndTimeLessThanGlobalTtl()).freezeDateAndReturn();
    ingestionClient.ingestVariables(variables);
    dateFreezer().dateToFreeze(now).freezeDateAndReturn();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getAllStoredExternalVariables())
      .extracting(ExternalProcessVariableDto::getVariableId)
      .containsExactlyInAnyOrderElementsOf(variables.stream()
                                             .map(ExternalProcessVariableRequestDto::getId)
                                             .collect(toSet()));
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
