/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.awaitility.Awaitility;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.ProcessOverviewService.APP_CUE_DASHBOARD_SUFFIX;
import static org.camunda.optimize.service.onboardinglistener.OnboardingNotificationService.MAGIC_LINK_TEMPLATE;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessOverviewRetrievalIT extends AbstractIT {

  private static final String FIRST_PROCESS_DEFINITION_KEY = "firstProcessDefinition";
  private static final String SECOND_PROCESS_DEFINITION_KEY = "secondProcessDefinition";

  @Test
  public void magicLinkHasAppCueSuffixIfItsClickedForTheFirstTime() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(FIRST_PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessOverviewResponseDto> overviews = processOverviewClient.getProcessOverviews();

    // then
    assertThat(overviews).filteredOn(process -> process.getProcessDefinitionKey().equals(FIRST_PROCESS_DEFINITION_KEY))
      .singleElement()
      .satisfies(process -> assertThat(process.getLinkToDashboard()).isEqualTo(
        String.format(MAGIC_LINK_TEMPLATE, FIRST_PROCESS_DEFINITION_KEY, FIRST_PROCESS_DEFINITION_KEY)
          + APP_CUE_DASHBOARD_SUFFIX));
  }

  @Test
  public void magicLinkHasNoAppCueSuffixIfItHasBeenClickedBefore() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(SECOND_PROCESS_DEFINITION_KEY));
    importAllEngineEntitiesFromScratch();
    // "Click" the link once
    entitiesClient.getEntityNames(SECOND_PROCESS_DEFINITION_KEY, SECOND_PROCESS_DEFINITION_KEY, null, null);
    // Wait until everything is created
    Awaitility.given().ignoreExceptions()
      .timeout(5, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(collectionClient.getCollectionById(SECOND_PROCESS_DEFINITION_KEY)).isNotNull());

    // when
    final List<ProcessOverviewResponseDto> overviews = processOverviewClient.getProcessOverviews();

    // then
    assertThat(overviews).filteredOn(process -> process.getProcessDefinitionKey().equals(SECOND_PROCESS_DEFINITION_KEY))
      .singleElement()
      .satisfies(process -> assertThat(process.getLinkToDashboard()).isEqualTo(
        // No suffix
        String.format(MAGIC_LINK_TEMPLATE, SECOND_PROCESS_DEFINITION_KEY, SECOND_PROCESS_DEFINITION_KEY)));
  }
}
