/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.CorrelatedMessageDbQuery;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageDbModel;
import java.util.List;

public interface CorrelatedMessageMapper extends HistoryCleanupMapper {

  void insert(CorrelatedMessageDbModel correlatedMessage);

  void delete(Long messageKey);

  Long count(CorrelatedMessageDbQuery filter);

  List<CorrelatedMessageDbModel> search(CorrelatedMessageDbQuery filter);
}