/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.user_task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;


public abstract class AbstractUserTaskImportIT {

  protected static final String START_EVENT = "startEvent";
  protected static final String END_EVENT = "endEvent";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  protected ObjectMapper objectMapper;

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

  protected void changeUserTaskIdleDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final long idleDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        try {
          engineDatabaseRule.changeUserTaskAssigneeOperationTimestamp(
            historicUserTaskInstanceDto.getId(),
            historicUserTaskInstanceDto.getStartTime().plus(idleDuration, ChronoUnit.MILLIS)
          );
        } catch (SQLException e) {
          throw new OptimizeIntegrationTestException(e);
        }
      });
  }

  protected void changeUserTaskWorkDuration(final ProcessInstanceEngineDto processInstanceDto,
                                            final long workDuration) {
    engineRule.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(historicUserTaskInstanceDto -> {
        if (historicUserTaskInstanceDto.getEndTime() != null) {
          try {
            engineDatabaseRule.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getEndTime().minus(workDuration, ChronoUnit.MILLIS)
            );
          } catch (SQLException e) {
            throw new OptimizeIntegrationTestException(e);
          }
        }
      });
  }

  protected ProcessInstanceEngineDto deployAndStartOneUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  protected ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  protected SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(elasticsearchType)
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }


}
