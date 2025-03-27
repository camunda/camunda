/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class FlowNodeTypeTest {

  @ParameterizedTest
  @EnumSource(BpmnElementType.class)
  void shouldContainBpmnElementType(final BpmnElementType bpmnElementType) {
    // when
    final FlowNodeType flowNodeType = FlowNodeType.fromZeebeBpmnElementType(bpmnElementType.name());

    // then
    assertThat(flowNodeType)
        .describedAs(
            """
            The enum FlowNodeType should contain a value for: %s. \
            Probably, the BPMN element is new and need to be added.""",
            bpmnElementType)
        .isNotNull()
        .isNotEqualTo(FlowNodeType.UNKNOWN);
  }
}
