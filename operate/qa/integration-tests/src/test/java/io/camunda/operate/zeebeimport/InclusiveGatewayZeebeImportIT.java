/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import org.junit.Test;

public class InclusiveGatewayZeebeImportIT extends OperateZeebeAbstractIT {

  @Test
  public void shouldImportIntermediateThrowEvent() {

    final String bpmnProcessId = "inclusiveGateway";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(bpmnProcessId)
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
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance(bpmnProcessId, "{\"flows\": [1,2]}")
        .waitUntil()
        .processInstanceIsFinished();

    // when
    final List<FlowNodeInstanceEntity> flowNodes =
        tester.getAllFlowNodeInstances(tester.getProcessInstanceKey());
    // then
    assertThat(map(flowNodes, FlowNodeInstanceEntity::getType))
        .isEqualTo(
            List.of(
                FlowNodeType.START_EVENT, FlowNodeType.INCLUSIVE_GATEWAY, FlowNodeType.END_EVENT));
  }
}
