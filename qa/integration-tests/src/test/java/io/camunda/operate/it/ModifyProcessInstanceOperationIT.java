/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.*;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessInstanceHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ModifyProcessInstanceOperationIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ModifyProcessInstanceHandler modifyProcessInstanceHandler;

  @Before
  public void before() {
    super.before();
    modifyProcessInstanceHandler.setZeebeClient(super.getClient());
    mockMvc = mockMvcTestRule.getMockMvc();
    tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil().processIsDeployed();
  }
  @Test
  public void shouldCancelToken() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().flowNodeIsActive("taskA");
    // when
    final List<Modification> modifications =
        List.of(new Modification()
            .setModification(Modification.Type.CANCEL_TOKEN)
            .setFromFlowNodeId("taskA"));

    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
  }

  @Test
  public void shouldAddToken() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted();

    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskB")
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsActive("taskB");
    // then
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
  }

  @Test
  public void shouldMoveToken() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted()
        .and()
        .flowNodeIsActive("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    List<Modification> modifications = List.of(
       new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA").setToFlowNodeId("taskB")
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsActive("taskB");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
  }

  @Test
  public void shouldMoveTokenWithNewVariables() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted()
        .and()
        .flowNodeIsActive("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA").setToFlowNodeId("taskB")
            .setVariables(Map.of("taskB",
                List.of(
                    Map.of("number", 1,
                           "title", "Modification"))))
    );
    final Long flowNodeInstanceKeyForTaskB = tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsActive("taskB")
        .getFlowNodeInstanceKeyFor("taskB");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getVariable("number", flowNodeInstanceKeyForTaskB)).isEqualTo("1");
    assertThat(tester.getVariable("title", flowNodeInstanceKeyForTaskB)).isEqualTo("\"Modification\"");
  }

  @Test
  public void shouldAddVariable() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted();

    assertThat(tester.getVariable("new-var")).isNull();
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.ADD_VARIABLE)
            .setVariables(Map.of("new-var","new-value"))
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .variableExists("new-var");

    // then
    assertThat(tester.getVariable("new-var")).isEqualTo("\"new-value\"");
  }

  @Test
  public void shouldAddVariableToFlowNodeScope() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil().flowNodeIsActive("taskA");

    Long flowNodeInstanceId = tester.getFlowNodeInstanceKeyFor( "taskA");
    assertThat(tester.getVariable("new-var", flowNodeInstanceId)).isNull();
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.ADD_VARIABLE)
            .setScopeKey(flowNodeInstanceId)
            .setVariables(Map.of("new-var","new-value"))
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .variableExistsIn("new-var", flowNodeInstanceId);
    // then
    assertThat(tester.getVariable("new-var", flowNodeInstanceId)).isEqualTo("\"new-value\"");
  }

  @Test
  public void shouldEditVariable() throws Exception{
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted();

    assertThat(tester.getVariable("a")).isEqualTo("\"b\"");
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.EDIT_VARIABLE)
            .setVariables(Map.of("a","c"))
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .variableHasValue("a","c");
    // then
    assertThat(tester.getVariable("a")).isEqualTo("\"c\"");
  }


  @Test
  public void shouldDoListOfModifications() throws Exception{
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil().flowNodeIsActive("taskA")
        .and().waitUntil().flowNodeIsActive("taskD");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskC")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.ACTIVE);
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.CANCEL_TOKEN)
            .setFromFlowNodeId("taskA"),
        new Modification()
            .setModification(Modification.Type.CANCEL_TOKEN)
            .setFromFlowNodeId("taskD"),
       new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskB"),
        new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskC")
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsTerminated("taskD");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskC")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.TERMINATED);
  }

  @Test
  public void shouldDoAListOfAllModifications() throws Exception{
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted()
        .and().waitUntil().flowNodeIsActive("taskA")
        .and().waitUntil().flowNodeIsActive("taskD");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskC")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.ACTIVE);
    // when
    List<Modification> modifications = List.of(
        new Modification()
            .setModification(Modification.Type.CANCEL_TOKEN)
            .setFromFlowNodeId("taskD"),
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA").setToFlowNodeId("taskB"),
        new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskC"),
        new Modification()
            .setModification(Modification.Type.ADD_VARIABLE)
            .setVariables(Map.of("answer","42"))
    );
    tester.modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted() // TODO: Implement operation complete by checking events
        .and()
        .flowNodeIsTerminated("taskA");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskC")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getVariable("answer")).isEqualTo("\"42\"");
  }
  //TODO: Testcases from miro board
  @Test
  public void shouldMoveTokenToSubprocessesWithVariablesForParentNodes() throws Exception {
    // Given
    tester
        .deployProcess("subProcess.bpmn")
        .waitUntil().processIsDeployed();

    final Long processInstanceKey = tester.startProcessInstance("prWithSubprocess")
        .waitUntil()
        .flowNodeIsActive("taskA")
        .getProcessInstanceKey();

    final Modification moveToken = new Modification()
        .setModification(Modification.Type.MOVE_TOKEN)
        .setFromFlowNodeId("taskA").setToFlowNodeId("taskB")
        .setVariables(Map.of(
            "subprocess", List.of(
                Map.of("sub", "way")),
            "innerSubprocess", List.of(
                Map.of("innerSub","innerWay"))
            )
        );

    // when
    tester.modifyProcessInstanceOperation(List.of(moveToken))
        .waitUntil().operationIsCompleted()
        .and().flowNodeIsActive("taskB")
        .and().flowNodeIsActive("subprocess")
        .and().flowNodeIsActive("innerSubprocess");

    final Long subprocessKey = tester.getFlowNodeInstanceKeyFor("subprocess");
    final Long innerSubprocessKey = tester.getFlowNodeInstanceKeyFor("innerSubprocess");
    tester.waitUntil()
        .variableExistsIn("sub", subprocessKey)
        .and().variableExistsIn("innerSub", innerSubprocessKey);

    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("subprocess")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("innerSubprocess")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getVariable("sub", subprocessKey )).isEqualTo("\"way\"");
    assertThat(tester.getVariable("innerSub", innerSubprocessKey)).isEqualTo("\"innerWay\"");
  }

}
