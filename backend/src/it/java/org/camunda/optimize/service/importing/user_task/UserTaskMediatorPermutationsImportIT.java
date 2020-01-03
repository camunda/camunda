/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.engine.importing.EngineImportScheduler;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.IdentityLinkLogEngineImportMediator;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

public class UserTaskMediatorPermutationsImportIT extends AbstractIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  private ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        ConfigurationServiceBuilder.createDefaultConfiguration()
      ).createOptimizeMapper();
    }
  }

  @ParameterizedTest(name = "is fully imported with mediator order {0}")
  @MethodSource("getParameters")
  public void isFullyImported(List<String> mediatorOrder) throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    performOrderedImport(mediatorOrder);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp =
      elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value, is(1L));
    for (SearchHit searchHitFields : idsResp.getHits()) {
      final ProcessInstanceDto persistedProcessInstanceDto = objectMapper.readValue(
        searchHitFields.getSourceAsString(), ProcessInstanceDto.class
      );
      assertThat(persistedProcessInstanceDto.getEvents(), is(not(empty())));
      assertThat(persistedProcessInstanceDto.getStartDate(), is(notNullValue()));
      assertThat(persistedProcessInstanceDto.getEndDate(), is(notNullValue()));
      assertThat(persistedProcessInstanceDto.getState(), is(notNullValue()));
      persistedProcessInstanceDto.getUserTasks()
        .forEach(userTask -> {
          assertThat(userTask.getStartDate(), is(notNullValue()));
          assertThat(userTask.getEndDate(), is(notNullValue()));
          assertThat(userTask.getIdleDurationInMs(), is(idleDuration));
          assertThat(userTask.getWorkDurationInMs(), is(userTask.getTotalDurationInMs() - idleDuration));
          assertThat(userTask.getAssigneeOperations().size(), is(1));
          assertThat(userTask.getClaimDate(), is(userTask.getAssigneeOperations().get(0).getTimestamp()));
        });
    }
  }

  private void performOrderedImport(List<String> mediatorOrder) {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerFactory()
      .getImportSchedulers()) {
      final List<EngineImportMediator> sortedMediators = scheduler
        .getImportMediators()
        .stream()
        .sorted(Comparator.comparingInt(o -> mediatorOrder.indexOf(o.getClass())))
        .collect(toList());

      sortedMediators.forEach(EngineImportMediator::importNextPage);
    }
    embeddedOptimizeExtension.makeSureAllScheduledJobsAreFinished();
  }

  private void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto, final long idleDuration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
            historicUserTaskInstanceDto.getId(),
            historicUserTaskInstanceDto.getStartTime().plus(idleDuration, ChronoUnit.MILLIS)
          );
        } catch (SQLException e) {
          throw new OptimizeIntegrationTestException(e);
        }
      });
  }

  private ProcessInstanceEngineDto deployUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  private static Stream<? extends Object> getParameters() {
    return StreamSupport.stream(Collections2.permutations(
      ImmutableList.of(
        CompletedUserTaskEngineImportMediator.class.getSimpleName(),
        IdentityLinkLogEngineImportMediator.class.getSimpleName(),
        CompletedProcessInstanceEngineImportMediator.class.getSimpleName()
      )).spliterator(), false);
  }

}
