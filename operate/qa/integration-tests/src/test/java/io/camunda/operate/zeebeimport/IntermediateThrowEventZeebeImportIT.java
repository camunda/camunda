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
import java.util.List;
import org.junit.Test;
import org.springframework.test.annotation.IfProfileValue;

public class IntermediateThrowEventZeebeImportIT extends OperateZeebeAbstractIT {

  @Test
  @IfProfileValue(name = "spring.profiles.active", value = "test")
  public void shouldImportIntermediateThrowEvent() {
    // given
    tester
        .deployProcess("intermediate-throw-event.bpmn")
        .waitUntil()
        .processIsDeployed()
        .then()
        .startProcessInstance("intermediate-throw-event-process", null)
        .waitUntil()
        .processInstanceIsFinished();

    // when
    final List<FlowNodeInstanceEntity> flowNodes =
        tester.getAllFlowNodeInstances(tester.getProcessInstanceKey());
    // then
    assertThat(map(flowNodes, FlowNodeInstanceEntity::getType))
        .isEqualTo(
            List.of(
                FlowNodeType.START_EVENT,
                FlowNodeType.INTERMEDIATE_THROW_EVENT,
                FlowNodeType.END_EVENT));
  }
}
