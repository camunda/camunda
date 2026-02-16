/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.ProcessInstanceDependantMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceDependantMapper.DeleteProcessInstanceRelatedDataDto;
import io.camunda.db.rdbms.sql.ProcessInstanceDependantMapper.DeleteRootProcessInstanceRelatedDataDto;
import java.util.List;

public abstract class ProcessInstanceDependant {

  private final ProcessInstanceDependantMapper processInstanceDependantMapper;

  public ProcessInstanceDependant(
      final ProcessInstanceDependantMapper processInstanceDependantMapper) {
    this.processInstanceDependantMapper = processInstanceDependantMapper;
  }

  public int deleteProcessInstanceRelatedData(
      final List<Long> processInstanceKeys, final int limit) {
    return processInstanceDependantMapper.deleteProcessInstanceRelatedData(
        new DeleteProcessInstanceRelatedDataDto(processInstanceKeys, limit));
  }

  public int deleteRootProcessInstanceRelatedData(
      final List<Long> rootProcessInstanceKeys, final int limit) {
    return processInstanceDependantMapper.deleteRootProcessInstanceRelatedData(
        new DeleteRootProcessInstanceRelatedDataDto(rootProcessInstanceKeys, limit));
  }
}
