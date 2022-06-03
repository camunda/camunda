/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.digest;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessDigestRetrievalIT extends AbstractIT {

  private static final String DEF_KEY = "aProcessDefKey";

  @Test
  public void getProcessDigests() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      "kermit",
      RESOURCE_TYPE_USER
    );
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram("anotherProcess"));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, DEFAULT_USERNAME);
    processOverviewClient.updateProcessOwner("anotherProcess", "kermit");

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews();

    // then
    assertThat(processes).hasSize(2)
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactlyInAnyOrder(
        Tuple.tuple(
          DEF_KEY,
          new ProcessDigestResponseDto(new AlertInterval(1, AlertIntervalUnit.WEEKS), false
          )
        ),
        Tuple.tuple(
          "anotherProcess",
          new ProcessDigestResponseDto(new AlertInterval(1, AlertIntervalUnit.WEEKS), false)
        )
      );
  }

}
