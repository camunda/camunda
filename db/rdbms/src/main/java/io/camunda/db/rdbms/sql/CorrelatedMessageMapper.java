/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CorrelatedMessageMapper
    extends HistoryCleanupMapper, ProcessBasedHistoryCleanupMapper {

  void insert(CorrelatedMessageDbModel correlatedMessage);

  void update(CorrelatedMessageDbModel correlatedMessage);

  int cleanupHistory(CleanupHistoryDto cleanupHistoryDto);
}
