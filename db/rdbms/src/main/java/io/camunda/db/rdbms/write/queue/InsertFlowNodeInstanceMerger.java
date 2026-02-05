/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;

public class InsertFlowNodeInstanceMerger extends BatchInsertMerger<FlowNodeInstanceDbModel> {

  public InsertFlowNodeInstanceMerger(
      final FlowNodeInstanceDbModel flowNodeInstance, final int maxBatchSize) {
    super(ContextType.FLOW_NODE, flowNodeInstance, maxBatchSize);
  }
}
