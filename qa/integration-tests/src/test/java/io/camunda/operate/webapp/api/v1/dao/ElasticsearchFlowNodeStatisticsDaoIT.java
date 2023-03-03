/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ElasticsearchFlowNodeStatisticsDaoIT extends OperateZeebeIntegrationTest {

  @Autowired
  ElasticsearchFlowNodeStatisticsDao dao;

  private List<FlowNodeStatistics> flowNodeStatistics;
  private Long demoProcessKey;
  private Long processInstanceKey;

  @Before
  public void setUp() {
    demoProcessKey = tester.deployProcess("demoProcess_v_2.bpmn").waitUntil().processIsDeployed().getProcessDefinitionKey();
  }

  protected Long createIncidentsAndGetProcessInstanceKey(String bpmnProcessId, String taskId, String errorMessage) {
    return tester.startProcessInstance(bpmnProcessId, "{\"a\": \"b\"}").waitUntil().processInstanceIsStarted()
        .and()
        .failTask(taskId, errorMessage).waitUntil().incidentIsActive()
        .and()
        .getProcessInstanceKey();
  }

  @Test
  public void shouldReturnEmptyListWhenNoProcessInstanceExist() throws Exception {
    given(() -> { /*"no sequence flows "*/ });
    when(() -> flowNodeStatistics = dao.getFlowNodeStatisticsForProcessInstance(123L));
    then(() -> {
      assertThat(flowNodeStatistics).isEmpty();
    });
  }

  @Test
  public void shouldReturnFlowNodeStatistics() throws Exception {
    given(() -> processInstanceKey = createIncidentsAndGetProcessInstanceKey("demoProcess", "taskA", "Some error"));
    when(() -> flowNodeStatistics = dao.getFlowNodeStatisticsForProcessInstance(processInstanceKey));
    then(() -> {
      assertThat(flowNodeStatistics).hasSize(4);
      assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("start")).findFirst().orElseThrow().getCompleted()).isEqualTo(1);
      assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("taskA")).findFirst().orElseThrow().getIncidents()).isEqualTo(1);
      assertThat(flowNodeStatistics.stream().filter(x -> x.getActivityId().equals("taskD")).findFirst().orElseThrow().getActive()).isEqualTo(1);
    });
  }

  protected void given(Runnable conditions) throws Exception {
    conditions.run();
  }

  protected void when(Runnable actions) throws Exception {
    actions.run();
  }

  protected void then(Runnable asserts) throws Exception {
    asserts.run();
  }
}
