/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.sql.VariableMapper.BatchInsertVariablesDto;
import io.camunda.db.rdbms.write.domain.VariableDbModel;

public class InsertVariableMerger
    extends BatchInsertMerger<BatchInsertVariablesDto, VariableDbModel> {

  public InsertVariableMerger(final VariableDbModel variable, final int maxBatchSize) {
    super(
        ContextType.VARIABLE,
        BatchInsertVariablesDto.class,
        variable,
        dto -> dto.variables().size(),
        maxBatchSize);
  }
}
