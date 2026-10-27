/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group.value;

public class FlowNodesGroupByValueDto implements ProcessGroupByValueDto {

  @Override
  public boolean isCombinable(final Object o) {
    return o instanceof UserTasksGroupByValueDto || o instanceof FlowNodesGroupByValueDto;
  }
}
