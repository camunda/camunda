/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.process;

import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.goals.ProcessDurationGoalDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.service.util.IdGenerator;
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
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessGoalsUpdateIT extends AbstractProcessGoalsIT {

  private static final String DEF_KEY = FIRST_PROCESS_DEFINITION_KEY;

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
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createProcessGoals_notAuthorizedForDefinition() {
    // given
    final String notAuthorizedDefinitionKey = "noAccess";
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    elasticSearchIntegrationTestExtension.addEventProcessDefinitionDtoToElasticsearch(
      notAuthorizedDefinitionKey, "name");
    addDefinitionToOptimize(notAuthorizedDefinitionKey);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(notAuthorizedDefinitionKey, Collections.emptyList())
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("validGoalsLists")
  public void createProcessGoals(final List<ProcessDurationGoalDto> goals) {
    // given
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidGoalsLists")
  public void createProcessGoals_invalidGoalsList(final List<ProcessDurationGoalDto> goals) {
    // given
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY));
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
    importAllEngineEntitiesFromScratch();

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(
        DEF_KEY,
        List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.DAYS))
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
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
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    setGoalsForProcess(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.DAYS)));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidGoalsLists")
  public void updateExistingProcessGoals_invalidGoals(final List<ProcessDurationGoalDto> goals) {
    // given
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram(DEF_KEY));
    importAllEngineEntitiesFromScratch();
    setGoalsForProcess(DEF_KEY, List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.DAYS)));

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateProcessGoalsRequest(DEF_KEY, goals)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private static Stream<List<ProcessDurationGoalDto>> validGoalsLists() {
    return Stream.of(
      Collections.emptyList(),
      List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.DAYS)),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, DateUnit.MONTHS)),
      List.of(
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.MONTHS),
        new ProcessDurationGoalDto(TARGET_DURATION, 99., 1, DateUnit.MONTHS)
      )
    );
  }

  private static Stream<List<ProcessDurationGoalDto>> invalidGoalsLists() {
    return Stream.of(
      List.of(new ProcessDurationGoalDto(SLA_DURATION, -5., 5, DateUnit.DAYS)),
      List.of(new ProcessDurationGoalDto(SLA_DURATION, 50., -5, DateUnit.DAYS)),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 105., 1, DateUnit.MONTHS)),
      List.of(
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.MONTHS),
        new ProcessDurationGoalDto(SLA_DURATION, 50., 5, DateUnit.MONTHS)
      ),
      List.of(new ProcessDurationGoalDto(TARGET_DURATION, 50., 1, null)),
      List.of(new ProcessDurationGoalDto(null, 50., 1, DateUnit.MONTHS))
    );
  }

  private void addDefinitionToOptimize(final String defKey) {
    final ProcessDefinitionOptimizeDto unauthorizedDef = ProcessDefinitionOptimizeDto.builder()
      .id(IdGenerator.getNextId())
      .key(defKey)
      .version("1")
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml("xml")
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      unauthorizedDef.getId(),
      unauthorizedDef
    );
  }

}
