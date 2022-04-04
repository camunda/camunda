/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.SLA_DURATION;
import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.TARGET_DURATION;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.DAYS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.MONTHS;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class ProcessGoalsUpdateIT extends AbstractProcessGoalsIT {

  @Test
  public void createProcessGoals_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createProcessGoals_noDefinitionExistsForKey() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validGoalsLists")
  public void createProcessGoals(final List<ProcessDurationGoalDto> goals) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getProcessGoals()).extracting(process -> process.getDurationGoals().getGoals()).containsExactly(goals);
  }

  @ParameterizedTest
  @MethodSource("invalidGoalsLists")
  public void createProcessGoals_invalidGoalsList(final List<ProcessDurationGoalDto> goals) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createProcessGoalsForEventBasedProcess() {
    // given
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      DEF_KEY, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER));
    final List<ProcessDurationGoalDto> goals = List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DAYS));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getProcessGoals()).extracting(process -> process.getDurationGoals().getGoals()).containsExactly(goals);
  }

  @Test
  public void createProcessGoalsForEventBasedProcess_notAuthorized() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    final String defKey = "notAuthorized";
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      defKey, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER)
    );

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(defKey, Collections.emptyList())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validGoalsLists")
  public void updateExistingProcessGoals(final List<ProcessDurationGoalDto> goals) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setGoalsForProcess(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DAYS)));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getProcessGoals()).extracting(process -> process.getDurationGoals().getGoals()).containsExactly(goals);
  }

  @ParameterizedTest
  @MethodSource("invalidGoalsLists")
  public void updateExistingProcessGoals_invalidGoals(final List<ProcessDurationGoalDto> goals) {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setGoalsForProcess(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DAYS)));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateExistingProcessGoalsOwnerIsNotRemoved() {
    // given
    deploySimpleProcessDefinition(DEF_KEY);
    importAllEngineEntitiesFromScratch();
    setGoalsForProcess(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DAYS)));
    setOwnerForProcess(DEF_KEY, DEFAULT_USERNAME);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 2, MONTHS)))
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertExpectedProcessOwner(DEF_KEY, DEFAULT_USERNAME);
  }

  private static Stream<List<ProcessDurationGoalDto>> validGoalsLists() {
    return Stream.of(
      Collections.emptyList(),
      List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DAYS)),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, MONTHS)),
      List.of(
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, MONTHS),
        new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, MONTHS)
      )
    );
  }

}
