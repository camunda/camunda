/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.goals;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.SLA_DURATION;
import static org.camunda.optimize.dto.optimize.query.goals.DurationGoalType.TARGET_DURATION;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.MILLIS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit.MONTHS;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessGoalsEvaluationIT extends AbstractProcessGoalsIT {

  @Test
  public void evaluateProcessGoals_notPossibleForUnauthenticatedUser() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void evaluateProcessGoals_noDefinitionExistsForKey() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("goalsAndExpectedEvaluationResults")
  public void evaluateProcessGoals_expectedEvaluationResult(final List<ProcessDurationGoalDto> goals,
                                                            final List<ProcessDurationGoalResultDto> expectedResults) {
    // given
    final ProcessDefinitionEngineDto processDef = deploySimpleProcessDefinition(DEF_KEY);
    final ProcessInstanceEngineDto procInst = engineIntegrationExtension.startProcessInstance(processDef.getId());
    final OffsetDateTime now = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(procInst.getId(), now.minusSeconds(10), now);
    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessDurationGoalResultDto> results = evaluateGoalsForProcess(DEF_KEY, goals);

    // then
    assertThat(results).containsExactlyElementsOf(expectedResults);
  }

  @ParameterizedTest
  @MethodSource("invalidGoalsLists")
  public void evaluationProcessGoals_invalidGoalSpecification(final List<ProcessDurationGoalDto> goals) {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void evaluateProcessGoals_notAuthorizedToAllDefinitionTenants() {
    // given
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY), null);
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY), OTHER_TENANT);
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void evaluateProcessGoals_notAuthorizedToEventBasedProcess() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      FIRST_PROCESS_DEFINITION_KEY, new IdentityDto(DEFAULT_USERNAME, IdentityType.USER));
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(DEF_KEY, Collections.emptyList())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private static Stream<Arguments> goalsAndExpectedEvaluationResults() {
    return Stream.of(
      Arguments.of(Collections.emptyList(), Collections.emptyList()),
      Arguments.of(
        List.of(new ProcessDurationGoalDto(SLA_DURATION, 25., 5, MILLIS)),
        List.of(new ProcessDurationGoalResultDto(SLA_DURATION, 10000L, false))
      ),
      Arguments.of(
        List.of(new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, MONTHS)),
        List.of(new ProcessDurationGoalResultDto(TARGET_DURATION, 10000L, true))
      ),
      Arguments.of(
        List.of(
          new ProcessDurationGoalDto(SLA_DURATION, 25, 5, MILLIS),
          new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, MONTHS)
        ),
        List.of(
          new ProcessDurationGoalResultDto(SLA_DURATION, 10000L, false),
          new ProcessDurationGoalResultDto(TARGET_DURATION, 10000L, true)
        )
      )
    );
  }

  private List<ProcessDurationGoalResultDto> evaluateGoalsForProcess(final String defKey,
                                                                     List<ProcessDurationGoalDto> goals) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateProcessGoalsRequest(defKey, goals)
      .executeAndReturnList(ProcessDurationGoalResultDto.class, Response.Status.OK.getStatusCode());
  }

}
