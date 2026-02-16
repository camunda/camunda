/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.AuditLogDbQuery;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import java.util.List;

public interface AuditLogMapper extends ProcessInstanceDependantMapper {

  void insert(BatchInsertDto<AuditLogDbModel> dto);

  Long count(AuditLogDbQuery filter);

  List<AuditLogDbModel> search(AuditLogDbQuery filter);

  int deleteProcessDefinitionRelatedData(List<Long> processDefinitionKeys, int limit);

  int cleanupHistory(HistoryCleanupMapper.CleanupHistoryDto dto);
}
