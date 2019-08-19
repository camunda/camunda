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
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.engine.importing.EngineImportScheduler;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedProcessInstanceEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.CompletedUserTaskEngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.UserOperationLogEngineImportMediator;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

@RunWith(Parameterized.class)
public class UserTaskMediatorPermutationsImportIT {

  @Parameterized.Parameters(name = "mediatorOrder: {0}")
  public static Iterable<? extends Object> data() {
    // Note: we use the simpleName here to have sane output for the junit name
    return Collections2.permutations(
      ImmutableList.of(
        CompletedUserTaskEngineImportMediator.class.getSimpleName(),
        UserOperationLogEngineImportMediator.class.getSimpleName(),
        CompletedProcessInstanceEngineImportMediator.class.getSimpleName()
      )
    );
  }

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String USER_TASK_1 = "userTask1";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  private ObjectMapper objectMapper;
  private final List<Class<? extends EngineImportMediator>> mediatorOrder;

  public UserTaskMediatorPermutationsImportIT(final List<String> mediatorClassOrder) {
    this.mediatorOrder = mapToMediatorClassList(mediatorClassOrder);
  }

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Before
  public void setUp() throws Exception {
    if (objectMapper == null) {
      objectMapper = new ObjectMapperFactory(
        new OptimizeDateTimeFormatterFactory().getObject(),
        new ConfigurationService()
      ).createOptimizeMapper();
    }
  }

  @Test
  public void isFullyImported() throws IOException {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployUserTaskProcess();
    engineRule.finishAllRunningUserTasks();
    final long idleDuration = 500;
    changeUserTaskIdleDuration(processInstanceDto, idleDuration);

    // when
    performOrderedImport();
    elasticSearchRule.refreshAllOptimizeIndices();

    // then
    final SearchResponse idsResp = elasticSearchRule.getSearchResponseForAllDocumentsOfType(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits(), is(1L));
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
          assertThat(userTask.getUserOperations().size(), is(2));
        });
    }
  }

  private void performOrderedImport() {
    for (EngineImportScheduler scheduler : embeddedOptimizeRule.getImportSchedulerFactory().getImportSchedulers()) {
      final List<EngineImportMediator> sortedMediators = scheduler
        .getImportMediators()
        .stream()
        .sorted(Comparator.comparingInt(o -> mediatorOrder.indexOf(o.getClass())))
        .collect(toList());

      sortedMediators.forEach(EngineImportMediator::importNextPage);
    }
    embeddedOptimizeRule.makeSureAllScheduledJobsAreFinished();
  }

  private List<Class<? extends EngineImportMediator>> mapToMediatorClassList(final List<String> simpleMediatorNames) {
    return simpleMediatorNames.stream()
      .map(simpleName -> {
        try {
          return getClass().getClassLoader().loadClass(
            EngineImportMediator.class.getName().replace(EngineImportMediator.class.getSimpleName(), simpleName)
          );
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      })
      .map(result -> (Class<EngineImportMediator>) result)
      .collect(toList());
  }

  private void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto, final long idleDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseRule.changeUserTaskClaimOperationTimestamp(
            processInstanceDto.getId(),
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
    return engineRule.deployAndStartProcess(processModel);
  }

}
