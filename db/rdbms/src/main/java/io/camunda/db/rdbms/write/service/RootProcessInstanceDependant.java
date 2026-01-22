/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.RootProcessInstanceDependantMapper;
import io.camunda.db.rdbms.sql.RootProcessInstanceDependantMapper.DeleteRootProcessInstanceRelatedDataDto;
import java.util.List;

public abstract class RootProcessInstanceDependant {

  private final RootProcessInstanceDependantMapper rootProcessInstanceDependantMapper;

  public RootProcessInstanceDependant(
      final RootProcessInstanceDependantMapper rootProcessInstanceDependantMapper) {
    this.rootProcessInstanceDependantMapper = rootProcessInstanceDependantMapper;
  }

  public int deleteRootProcessInstanceRelatedData(
      final int partitionId, final List<Long> rootProcessInstanceKeys, final int limit) {
    return rootProcessInstanceDependantMapper.deleteRootProcessInstanceRelatedData(
        new DeleteRootProcessInstanceRelatedDataDto(partitionId, rootProcessInstanceKeys, limit));
  }
}
