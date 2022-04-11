/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;

public class AnalysisRestServiceIT extends AbstractIT {

  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String PROCESS_DEFINITION_KEY = "procDef";
  private static final String PROCESS_DEFINITION_VERSION_1 = "1";
  private static final String PROCESS_DEFINITION_VERSION_2 = "2";
  private static final String END_ACTIVITY = "endActivity";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String TASK = "task_1";

  @Test
  public void getCorrelationWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessDefinitionCorrelation(new BranchAnalysisRequestDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getCorrelation() throws IOException {
    // given
    setupFullInstanceFlow();

    // when
    BranchAnalysisRequestDto branchAnalysisRequestDto = new BranchAnalysisRequestDto();
    branchAnalysisRequestDto.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    branchAnalysisRequestDto.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1);
    branchAnalysisRequestDto.setGateway(GATEWAY_ACTIVITY);
    branchAnalysisRequestDto.setEnd(END_ACTIVITY);

    BranchAnalysisResponseDto response = analysisClient.getProcessDefinitionCorrelation(branchAnalysisRequestDto);

    // then
    assertThat(response)
      .isNotNull()
      .extracting(BranchAnalysisResponseDto::getTotal)
      .isEqualTo(2L);
  }

  private void setupFullInstanceFlow() throws IOException {
    final ProcessDefinitionOptimizeDto processDefinitionXmlDto = ProcessDefinitionOptimizeDto.builder()
      .id(PROCESS_DEFINITION_ID)
      .key(PROCESS_DEFINITION_KEY)
      .version(PROCESS_DEFINITION_VERSION_1)
      .dataSource(new EngineDataSourceDto(DEFAULT_ENGINE_ALIAS))
      .bpmn20Xml(readDiagram())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      PROCESS_DEFINITION_ID,
      processDefinitionXmlDto
    );

    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    processDefinitionXmlDto.setVersion(PROCESS_DEFINITION_VERSION_2);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      PROCESS_DEFINITION_ID_2,
      processDefinitionXmlDto
    );

    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processDefinitionId(PROCESS_DEFINITION_ID)
      .processDefinitionKey(PROCESS_DEFINITION_KEY)
      .processDefinitionVersion(PROCESS_DEFINITION_VERSION_1)
      .processInstanceId(PROCESS_INSTANCE_ID)
      .startDate(OffsetDateTime.now())
      .endDate(OffsetDateTime.now())
      .flowNodeInstances(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK}))
      .build();
    embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .createIndexIfMissing(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient(),
        new ProcessInstanceIndex(PROCESS_DEFINITION_KEY)
      );
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      getProcessInstanceIndexAliasName(PROCESS_DEFINITION_KEY), PROCESS_INSTANCE_ID, procInst);

    procInst.setFlowNodeInstances(
      createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      getProcessInstanceIndexAliasName(PROCESS_DEFINITION_KEY), PROCESS_INSTANCE_ID_2, procInst);
  }

  private List<FlowNodeInstanceDto> createEventList(String[] activityIds) {
    final List<FlowNodeInstanceDto> events = new ArrayList<>(activityIds.length);
    for (String activityId : activityIds) {
      FlowNodeInstanceDto flowNodeInstance = new FlowNodeInstanceDto();
      flowNodeInstance.setFlowNodeId(activityId);
      events.add(flowNodeInstance);
    }
    return events;
  }

  private String readDiagram() throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(AnalysisRestServiceIT.DIAGRAM));
  }

  private static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

}