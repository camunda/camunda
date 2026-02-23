/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import java.util.List;

/**
 * Mapper interface for deleting data related to process instances. As a rule of thumb, this
 * interface should be extended by mappers that should delete process instance related data upon a
 * process instance deletion.
 */
public interface ProcessInstanceDependantMapper {

  // for history deletion API
  int deleteProcessInstanceRelatedData(DeleteProcessInstanceRelatedDataDto dto);

  // for history cleanup
  int deleteRootProcessInstanceRelatedData(DeleteRootProcessInstanceRelatedDataDto dto);

  record DeleteProcessInstanceRelatedDataDto(List<Long> processInstanceKeys, int limit) {}

  record DeleteRootProcessInstanceRelatedDataDto(List<Long> rootProcessInstanceKeys, int limit) {}
}
