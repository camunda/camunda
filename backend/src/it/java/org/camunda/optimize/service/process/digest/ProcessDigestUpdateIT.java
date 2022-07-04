/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.digest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit.DAYS;
import static org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit.MONTHS;
import static org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit.WEEKS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class ProcessDigestUpdateIT extends AbstractIT {

  private static final String DEF_KEY = "aProcessDefKey";

  @Test
  public void updateDigest_unauthenticatedUser() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, DEFAULT_USERNAME);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessDigestRequest(DEF_KEY, new ProcessDigestRequestDto(null, true))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void updateDigest_definitionDoesNotExist() {
    // when
    final Response response =
      processOverviewClient.updateProcessDigest(DEF_KEY, new ProcessDigestRequestDto(null, true));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void updateDigest_userIsNotOwner() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      DEFAULT_USERNAME,
      "kermit",
      RESOURCE_TYPE_USER
    );
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, "kermit");

    // when
    final Response response =
      processOverviewClient.updateProcessDigest(DEF_KEY, new ProcessDigestRequestDto(null, true));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidProcessDigests")
  public void updateDigest_invalidDigest(final ProcessDigestRequestDto invalidDigest) {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, DEFAULT_USERNAME);

    // when
    Response response = processOverviewClient.updateProcessDigest(DEF_KEY, invalidDigest);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateDigest_checkIntervalIsOptional() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, DEFAULT_USERNAME);
    processOverviewClient.updateProcessDigest(
      DEF_KEY,
      new ProcessDigestRequestDto(new AlertInterval(5, MONTHS), false)
    );

    // when
    final List<ProcessOverviewResponseDto> processes = processOverviewClient.getProcessOverviews(null);

    // then
    assertThat(processes).singleElement()
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactly(DEF_KEY, new ProcessDigestResponseDto(new AlertInterval(5, MONTHS), false));

    // when updating without checkInterval
    processOverviewClient.updateProcessDigest(DEF_KEY, new ProcessDigestRequestDto(null, true));

    // when
    final List<ProcessOverviewResponseDto> processesAfterUpdate = processOverviewClient.getProcessOverviews(null);

    // then
    assertThat(processesAfterUpdate).singleElement()
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactly(DEF_KEY, new ProcessDigestResponseDto(new AlertInterval(5, MONTHS), true));
  }

  @Test
  public void updateDigest() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    processOverviewClient.updateProcessOwner(DEF_KEY, DEFAULT_USERNAME);

    // when
    final List<ProcessOverviewResponseDto> processesBeforeUpdate = processOverviewClient.getProcessOverviews(null);

    // then
    assertThat(processesBeforeUpdate).singleElement()
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactly(DEF_KEY, new ProcessDigestResponseDto(new AlertInterval(1, WEEKS), false));

    // when
    processOverviewClient.updateProcessDigest(DEF_KEY, new ProcessDigestRequestDto(new AlertInterval(2, DAYS), true));

    // when
    final List<ProcessOverviewResponseDto> processesAfterUpdate = processOverviewClient.getProcessOverviews(null);

    // then
    assertThat(processesAfterUpdate).singleElement()
      .extracting(ProcessOverviewResponseDto::getProcessDefinitionKey, ProcessOverviewResponseDto::getDigest)
      .containsExactly(DEF_KEY, new ProcessDigestResponseDto(new AlertInterval(2, DAYS), true));
  }

  @SuppressWarnings(UNUSED)
  private static Stream<ProcessDigestRequestDto> invalidProcessDigests() {
    return Stream.of(
      new ProcessDigestRequestDto(null, null),
      null
    );
  }

}
