/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.FlowNodeDistributedByValueDto;

public class FlowNodeDistributedByDto extends ProcessDistributedByDto<FlowNodeDistributedByValueDto> {

  public FlowNodeDistributedByDto() {
    this.type = DistributedByType.FLOW_NODE;
  }

  @Override
  public boolean isCombinable(final Object o) {
    return DistributedByType.FLOW_NODE.equals(type)
      && DistributedByType.FLOW_NODE.equals(o);
  }
}
