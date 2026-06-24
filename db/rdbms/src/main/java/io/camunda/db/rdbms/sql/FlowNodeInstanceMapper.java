/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import java.time.OffsetDateTime;
import java.util.List;

public interface FlowNodeInstanceMapper extends ProcessInstanceDependantMapper {

  void insert(BatchInsertDto<FlowNodeInstanceDbModel> dto);

  void updateStateAndEndDate(EndFlowNodeDto dto);

  void updateIncident(UpdateIncidentDto dto);

  Long count(FlowNodeInstanceDbQuery filter);

  List<FlowNodeInstanceDbModel> search(FlowNodeInstanceDbQuery filter);

  record EndFlowNodeDto(
      long flowNodeInstanceKey,
      FlowNodeInstanceEntity.FlowNodeState state,
      OffsetDateTime endDate) {}

  record UpdateIncidentDto(long flowNodeInstanceKey, Long incidentKey) {}
}
