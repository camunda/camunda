/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessInstanceHandler;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ModifyProcessInstanceOperationZeebeIT extends OperateZeebeAbstractIT {

  @Autowired private ModifyProcessInstanceHandler modifyProcessInstanceHandler;

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  @Override
  @Before
  public void before() {
    super.before();
    modifyProcessInstanceHandler.setCamundaClient(super.getClient());
    mockMvc = mockMvcTestRule.getMockMvc();
    tester.deployProcess("demoProcess_v_2.bpmn").waitUntil().processIsDeployed();
  }

  @Test
  public void shouldCancelTokenForFlowNodeId() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .flowNodeIsActive("taskA");
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.CANCEL_TOKEN)
                .setFromFlowNodeId("taskA"));

    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
  }

  @Test
  public void shouldCancelTokenForFlowNodeInstanceKey() throws Exception {
    // given
    final long flowNodeInstanceKey =
        tester
            .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
            .waitUntil()
            .flowNodeIsActive("taskA")
            .then()
            .getFlowNodeInstanceKeyFor("taskA");

    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.CANCEL_TOKEN)
                .setFromFlowNodeInstanceKey("" + flowNodeInstanceKey));

    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA");
    // then
    final FlowNodeInstanceEntity flowNodeInstanceEntity =
        tester.getFlowNodeInstanceEntityFor(flowNodeInstanceKey);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.TERMINATED);
  }

  @Test
  public void shouldAddToken() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();

    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskB"));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsActive("taskB");
    // then
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
  }

  @Test
  public void shouldAddTokenWithVariables() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();

    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskB")
                .setVariables(Map.of("taskB", List.of(Map.of("c", "d")))));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsActive("taskB")
        .and()
        .variableExists("c");
    // then
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getVariable("c", tester.getFlowNodeInstanceKeyFor("taskB")))
        .isEqualTo("\"d\"");
  }

  @Ignore("Due to flaky CI tests")
  @Test
  public void shouldAddTokenWithAncestor() throws Exception {
    // given
    tester
        .deployProcess("subProcess.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("prWithSubprocess")
        .waitUntil()
        .flowNodeIsActive("taskA");

    final var moveToken =
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    tester
        .modifyProcessInstanceOperation(List.of(moveToken))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskB")
        .and()
        .flowNodeIsTerminated("taskA");

    var flowNodeInstances = tester.getAllFlowNodeInstances();
    final var subprocessInstanceKey =
        flowNodeInstances.stream()
            .filter(n -> n.getFlowNodeId().equals("subprocess"))
            .findFirst()
            .orElseThrow()
            .getKey();
    final var innerSubprocessInstanceKey =
        flowNodeInstances.stream()
            .filter(n -> n.getFlowNodeId().equals("innerSubprocess"))
            .findFirst()
            .orElseThrow()
            .getKey();

    var addWithAncestor =
        new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskB")
            .setAncestorElementInstanceKey(subprocessInstanceKey);
    tester
        .modifyProcessInstanceOperation(List.of(addWithAncestor))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .waitUntil()
        .flowNodesAreActive("taskB", 2);

    flowNodeInstances = tester.getAllFlowNodeInstances();
    assertThat(flowNodeInstances.size()).isEqualTo(6);

    addWithAncestor =
        new Modification()
            .setModification(Modification.Type.ADD_TOKEN)
            .setToFlowNodeId("taskB")
            .setAncestorElementInstanceKey(innerSubprocessInstanceKey);
    tester
        .modifyProcessInstanceOperation(List.of(addWithAncestor))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .waitUntil()
        .flowNodesAreActive("taskB", 3);
    flowNodeInstances = tester.getAllFlowNodeInstances();
    assertThat(flowNodeInstances.size()).isEqualTo(7);
  }

  @Test
  public void shouldAddTwoToken() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();

    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskB"),
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskB"));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodesAreActive("taskB", 2);
    // then
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeInstanceKeysFor("taskB").size()).isEqualTo(2);
  }

  @Test
  public void shouldMoveTokenFromFlowNodeId() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .flowNodeIsActive("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeId("taskA")
                .setToFlowNodeId("taskB"));
    tester
        .modifyProcessInstanceOperation(modifications)
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
  public void shouldMoveTokenFromFlowNodeInstanceKey() throws Exception {
    // given
    final Long flowNodeInstanceKey =
        tester
            .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .flowNodeIsActive("taskA")
            .then()
            .getFlowNodeInstanceKeyFor("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeInstanceKey("" + flowNodeInstanceKey)
                .setToFlowNodeId("taskB"));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsActive("taskB");
    // then
    assertThat(tester.getFlowNodeInstanceEntityFor(flowNodeInstanceKey).getState())
        .isEqualTo(FlowNodeState.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
  }

  @Test // 3389
  public void shouldMoveTokenWith2NewFlowNodeAndDifferentVariables() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA"))
        .isIn(FlowNodeStateDto.ACTIVE, FlowNodeStateDto.INCIDENT);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeId("taskA")
                .setToFlowNodeId("taskB")
                .setNewTokensCount(2)
                .setVariables(
                    Map.of(
                        "taskB",
                        List.of(
                            Map.of("var1", "val1", "var2", "val2"),
                            Map.of("var3", "val3", "var4", "val4")))));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodesAreActive("taskB", 2)
        .and()
        .variableExists("var1")
        .and()
        .variableExists("var2")
        .variableExists("var3")
        .and()
        .variableExists("var4");
    // then

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);

    // Different var scopes
    final var variables = varsToStrings(tester.getFlowNodeInstanceKeysFor("taskB"));
    assertThat(variables.get(0)).isEqualTo("[var1=\"val1\", var2=\"val2\"]");
    assertThat(variables.get(1)).isEqualTo("[var3=\"val3\", var4=\"val4\"]");
  }

  @Test
  public void shouldMoveTokenWithNewVariables() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .flowNodeIsActive("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeId("taskA")
                .setToFlowNodeId("taskB")
                .setVariables(
                    Map.of("taskB", List.of(Map.of("number", 1, "title", "Modification")))));
    final Long flowNodeInstanceKeyForTaskB =
        tester
            .modifyProcessInstanceOperation(modifications)
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
    assertThat(tester.getVariable("title", flowNodeInstanceKeyForTaskB))
        .isEqualTo("\"Modification\"");
  }

  @Test // NPE in cancelToken : https://github.com/camunda/operate/issues/3499
  public void shouldMoveTokenFailsDueMissingFlowNodeInstanceKeys() throws Exception {
    // given
    final var flowNodeInstanceKey =
        tester
            .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
            .waitUntil()
            .processInstanceIsStarted()
            .and()
            .flowNodeIsActive("taskA")
            .then()
            .getFlowNodeInstanceKeyFor("taskA");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    // when
    var modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeInstanceKey("" + flowNodeInstanceKey)
                .setToFlowNodeId("taskB"));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .then()
        .waitUntil()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsActive("taskB");
    // then
    assertThat(tester.getFlowNodeInstanceEntityFor(flowNodeInstanceKey).getState())
        .isEqualTo(FlowNodeState.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);

    //
    modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setNewTokensCount(1)
                .setFromFlowNodeId("taskA")
                .setToFlowNodeId("taskB"));
    tester.modifyProcessInstanceOperation(modifications).waitUntil().operationIsFailed();
    assertThat(tester.getFlowNodeInstanceEntityFor(flowNodeInstanceKey).getState())
        .isEqualTo(FlowNodeState.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(
            tester.getOperations().stream()
                .filter(
                    o ->
                        o.getErrorMessage()
                            .contains(
                                "Can't find not finished flowNodeInstance keys for process instance "))
                .count())
        .isEqualTo(1);
  }

  @Test
  public void shouldAddVariable() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();

    assertThat(tester.getVariable("new-var")).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_VARIABLE)
                .setVariables(Map.of("new-var", "new-value")));
    tester
        .modifyProcessInstanceOperation(modifications)
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
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskA");

    final Long flowNodeInstanceId = tester.getFlowNodeInstanceKeyFor("taskA");
    assertThat(tester.getVariable("new-var", flowNodeInstanceId)).isNull();
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_VARIABLE)
                .setScopeKey(flowNodeInstanceId)
                .setVariables(Map.of("new-var", "new-value")));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .variableExistsIn("new-var", flowNodeInstanceId);
    // then
    assertThat(tester.getVariable("new-var", flowNodeInstanceId)).isEqualTo("\"new-value\"");
  }

  @Test
  public void shouldEditVariable() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();

    assertThat(tester.getVariable("a")).isEqualTo("\"b\"");
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.EDIT_VARIABLE)
                .setVariables(Map.of("a", "c")));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .variableHasValue("a", "\"c\"");
    // then
    assertThat(tester.getVariable("a")).isEqualTo("\"c\"");
  }

  @Test
  public void shouldDoListOfModifications() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskA")
        .and()
        .waitUntil()
        .flowNodeIsActive("taskD");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskC")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.ACTIVE);
    // when
    final List<Modification> modifications =
        List.of(
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
                .setToFlowNodeId("taskC"));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodeIsTerminated("taskA")
        .flowNodeIsTerminated("taskD")
        .and()
        .flowNodeIsActive("taskB")
        .flowNodeIsActive("taskC");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskC")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.TERMINATED);
  }

  @Test
  public void shouldDoAListOfAllModifications() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskA")
        .and()
        .waitUntil()
        .flowNodeIsActive("taskD");

    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskB")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskC")).isNull();
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.ACTIVE);
    // when
    final List<Modification> modifications =
        List.of(
            new Modification()
                .setModification(Modification.Type.CANCEL_TOKEN)
                .setFromFlowNodeId("taskD"),
            new Modification()
                .setModification(Modification.Type.MOVE_TOKEN)
                .setFromFlowNodeId("taskA")
                .setToFlowNodeId("taskB"),
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId("taskC"),
            new Modification()
                .setModification(Modification.Type.ADD_VARIABLE)
                .setVariables(Map.of("answer", "42")));
    tester
        .modifyProcessInstanceOperation(modifications)
        .waitUntil()
        .operationIsCompleted() // TODO: Implement operation complete by checking events
        .and()
        .flowNodeIsTerminated("taskA")
        .flowNodeIsActive("taskB")
        .flowNodeIsActive("taskC")
        .flowNodeIsTerminated("taskD");
    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskC")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("taskD")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getVariable("answer")).isEqualTo("\"42\"");
  }

  @Test
  public void shouldMoveTokenToSubprocessesWithVariablesForParentNodes() throws Exception {
    // Given
    tester.deployProcess("subProcess.bpmn").waitUntil().processIsDeployed();

    tester
        .startProcessInstance("prWithSubprocess", "{\"items\": [1]}")
        .waitUntil()
        .flowNodeIsActive("taskA");

    final Modification moveToken =
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB")
            .setVariables(
                Map.of(
                    "subprocess", List.of(Map.of("sub", "way")),
                    "innerSubprocess", List.of(Map.of("innerSub", "innerWay"))));
    // when
    tester
        .modifyProcessInstanceOperation(List.of(moveToken))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodeIsActive("taskB")
        .and()
        .flowNodeIsActive("subprocess")
        .and()
        .flowNodeIsActive("innerSubprocess");

    final Long subprocessKey = tester.getFlowNodeInstanceKeyFor("subprocess");
    final Long innerSubprocessKey = tester.getFlowNodeInstanceKeyFor("innerSubprocess");
    tester
        .waitUntil()
        .variableExistsIn("sub", subprocessKey)
        .and()
        .variableExistsIn("innerSub", innerSubprocessKey);

    // then
    assertThat(tester.getFlowNodeStateFor("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    assertThat(tester.getFlowNodeStateFor("taskB")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("subprocess")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getFlowNodeStateFor("innerSubprocess")).isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(tester.getVariable("sub", subprocessKey)).isEqualTo("\"way\"");
    assertThat(tester.getVariable("innerSub", innerSubprocessKey)).isEqualTo("\"innerWay\"");
  }

  @Test
  public void shouldMoveTokenWithAncestor() throws Exception {
    // given
    tester
        .deployProcess("subProcess.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("prWithSubprocess")
        .waitUntil()
        .flowNodeIsActive("taskA");

    final var moveToken =
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    tester
        .modifyProcessInstanceOperation(List.of(moveToken))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .waitUntil()
        .flowNodeIsActive("taskB")
        .and()
        .flowNodeIsTerminated("taskA");

    var flowNodeInstances = tester.getAllFlowNodeInstances();
    final var subprocessInstanceKey =
        flowNodeInstances.stream()
            .filter(n -> n.getFlowNodeId().equals("subprocess"))
            .findFirst()
            .orElseThrow()
            .getKey();
    final var innerSubprocessInstanceKey =
        flowNodeInstances.stream()
            .filter(n -> n.getFlowNodeId().equals("innerSubprocess"))
            .findFirst()
            .orElseThrow()
            .getKey();

    final var moveWithAncestor =
        new Modification()
            .setModification(Modification.Type.MOVE_TOKEN)
            .setAncestorElementInstanceKey(subprocessInstanceKey)
            .setFromFlowNodeId("taskB")
            .setToFlowNodeId("taskC");

    tester
        .modifyProcessInstanceOperation(List.of(moveWithAncestor))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .waitUntil()
        .flowNodesAreActive("taskC", 1);

    flowNodeInstances = tester.getAllFlowNodeInstances();
    assertThat(flowNodeInstances.size()).isEqualTo(6);
  }

  @Test
  public void shouldMoveVariablesInDifferentScopes() throws Exception {
    // given
    tester
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .flowNodeIsActive("taskA");

    // when
    tester
        .modifyProcessInstanceOperation(
            List.of(
                new Modification()
                    .setModification(Modification.Type.MOVE_TOKEN)
                    .setFromFlowNodeId("taskA")
                    .setToFlowNodeId("taskC")
                    .setNewTokensCount(2)
                    .setVariables(Map.of("taskC", List.of(Map.of("test", 1), Map.of("test2", 2))))))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodeIsTerminated("taskA")
        .and()
        .flowNodesAreActive("taskC", 2);

    // then
    final var flowNodeStates = tester.getFlowNodeStates();
    assertThat(flowNodeStates.get("taskA")).isEqualTo(FlowNodeStateDto.TERMINATED);
    final var taskCFlowNodeKeys = tester.getFlowNodeInstanceKeysFor("taskC");
    final var varsAsStrings = varsToStrings(taskCFlowNodeKeys);
    assertThat(varsAsStrings.get(0)).isEqualTo("[test=1]");
    assertThat(varsAsStrings.get(1)).isEqualTo("[test2=2]");
  }

  // From
  // https://camunda.slack.com/archives/C0359JZEUV8/p1663911398178359?thread_ts=1663848113.283729&cid=C0359JZEUV8
  @Test
  public void shouldAddTokenAndNewScopesForEventSubprocess() throws Exception {
    // Given
    final String subprocessFlowNodeId = "eventSubprocess";
    final String eventSubprocessTaskFlowNodeId = "eventSubprocessTask";
    tester
        .deployProcess("develop/eventSubProcess_v_1.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("eventSubprocessProcess")
        .and()
        .flowNodeIsActive(subprocessFlowNodeId);

    // when
    tester.modifyProcessInstanceOperation(
        List.of(
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId(eventSubprocessTaskFlowNodeId),
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId(eventSubprocessTaskFlowNodeId),
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId(subprocessFlowNodeId),
            new Modification()
                .setModification(Modification.Type.ADD_TOKEN)
                .setToFlowNodeId(eventSubprocessTaskFlowNodeId)));
    // then
    tester
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodesAreActive(eventSubprocessTaskFlowNodeId, 5)
        .and()
        .flowNodesAreActive(subprocessFlowNodeId, 2);
    // check states
    final var flowNodeStates = tester.getFlowNodeStates();
    assertThat(flowNodeStates.get(eventSubprocessTaskFlowNodeId))
        .isEqualTo(FlowNodeStateDto.ACTIVE);
    assertThat(flowNodeStates.get(subprocessFlowNodeId)).isEqualTo(FlowNodeStateDto.ACTIVE);
    // check statistics
    final var statistics =
        flowNodeInstanceReader.getFlowNodeStatisticsForProcessInstance(
            tester.getProcessInstanceKey());
    final var eventSubProcessTaskStatistic =
        statistics.stream()
            .filter(s -> s.getActivityId().equals(eventSubprocessTaskFlowNodeId))
            .findFirst()
            .orElseThrow();
    final var eventSubProcessStatistic =
        statistics.stream()
            .filter(s -> s.getActivityId().equals(subprocessFlowNodeId))
            .findFirst()
            .orElseThrow();
    assertThat(eventSubProcessTaskStatistic.getActive()).isEqualTo(5);
    assertThat(eventSubProcessStatistic.getActive()).isEqualTo(2);
  }

  @Test
  public void shouldCancelMultiInstance() throws Exception {
    // Given
    tester
        .deployProcess("usertest/multiInstance_v_2.bpmn")
        .and()
        .startProcessInstance("multiInstanceProcess", "{ \"items\": [1,2,3]}")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .flowNodesAreActive("filterMapSubProcess", 3);
    // when
    tester
        .modifyProcessInstanceOperation(
            List.of(
                new Modification()
                    .setModification(Modification.Type.CANCEL_TOKEN)
                    .setFromFlowNodeId("filterMapSubProcess")))
        .waitUntil()
        .operationIsCompleted()
        .and()
        .flowNodeIsTerminated("filterMapSubProcess");

    assertThat(tester.getFlowNodeStateFor("filterMapSubProcess"))
        .isEqualTo(FlowNodeStateDto.TERMINATED);
  }

  private List<String> varsToStrings(final List<Long> flowNodeKeys) {
    final var variables =
        flowNodeKeys.stream()
            .map(key -> tester.getVariablesForScope(key))
            .collect(Collectors.toList());
    return variables.stream()
        .map(
            vars ->
                vars.stream()
                    .map(v -> v.getName() + "=" + v.getValue())
                    .collect(Collectors.toList())
                    .toString())
        .collect(Collectors.toList());
  }
}
