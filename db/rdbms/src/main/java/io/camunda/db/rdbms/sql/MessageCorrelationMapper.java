/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.write.domain.MessageCorrelationDbModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageCorrelationMapper
    extends HistoryCleanupMapper, ProcessBasedHistoryCleanupMapper {

  void insert(MessageCorrelationDbModel messageCorrelation);

  void update(MessageCorrelationDbModel messageCorrelation);

  int cleanupHistory(CleanupHistoryDto cleanupHistoryDto);
}
