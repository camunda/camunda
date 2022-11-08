/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Test;

import java.util.List;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;

public class InclusiveGatewayIT  extends OperateZeebeIntegrationTest {

  @Test
  public void shouldImportIntermediateThrowEvent(){

    String bpmnProcessId = "inclusiveGateway";
    BpmnModelInstance instance = Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .inclusiveGateway("gateway")
        .defaultFlow()
        .sequenceFlowId("flow1")
        .conditionExpression("= list contains(flows,\"1\")")
        .endEvent()
        .moveToLastGateway()
        .conditionExpression("= list contains(flows,\"2\")")
        .endEvent()
        .done();

    // given
    tester
        .deployProcess(instance, "inclusiveGateway.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance(bpmnProcessId, "{\"flows\": [1,2]}")
        .waitUntil()
        .processInstanceIsFinished();

    // when
    List<FlowNodeInstanceEntity> flowNodes = tester.getAllFlowNodeInstances(tester.getProcessInstanceKey());
    // then
    assertThat(map(flowNodes,FlowNodeInstanceEntity::getType)).isEqualTo(
        List.of(FlowNodeType.START_EVENT, FlowNodeType.INCLUSIVE_GATEWAY, FlowNodeType.END_EVENT));
  }

}
