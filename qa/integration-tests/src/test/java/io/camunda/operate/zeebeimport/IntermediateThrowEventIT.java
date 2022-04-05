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
import java.util.List;
import org.junit.Test;
import org.springframework.test.annotation.IfProfileValue;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;

public class IntermediateThrowEventIT extends OperateZeebeIntegrationTest {

  @Test
  @IfProfileValue(name="spring.profiles.active", value="test")
  public void shouldImportIntermediateThrowEvent(){
    // given
    tester
        .deployProcess("intermediate-throw-event.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("intermediate-throw-event-process", null)
        .waitUntil().processInstanceIsFinished();

    // when
    List<FlowNodeInstanceEntity> flowNodes = tester.getAllFlowNodeInstances(tester.getProcessInstanceKey());
    // then
    assertThat(map(flowNodes,FlowNodeInstanceEntity::getType)).isEqualTo(
        List.of(FlowNodeType.START_EVENT, FlowNodeType.INTERMEDIATE_THROW_EVENT, FlowNodeType.END_EVENT));
  }
}
