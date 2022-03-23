/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.FlowNodesGroupByValueDto;

import java.util.Objects;

public class FlowNodesGroupByDto extends ProcessGroupByDto<FlowNodesGroupByValueDto> {

  public FlowNodesGroupByDto() {
    this.type = ProcessGroupByType.FLOW_NODES;
  }

  @Override
  protected boolean isTypeCombinable(final ProcessGroupByDto<?> that) {
    return Objects.equals(type, that.type) || Objects.equals(that.type, ProcessGroupByType.USER_TASKS);
  }
}
