/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.FlowNodesGroupByValueDto;
import java.util.Objects;

public class FlowNodesGroupByDto extends ProcessGroupByDto<FlowNodesGroupByValueDto> {

  public FlowNodesGroupByDto() {
    this.type = ProcessGroupByType.FLOW_NODES;
  }

  @Override
  protected boolean isTypeCombinable(final ProcessGroupByDto<?> that) {
    return Objects.equals(type, that.type)
        || Objects.equals(that.type, ProcessGroupByType.USER_TASKS);
  }
}
