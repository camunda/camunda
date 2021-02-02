/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.processing;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.NextValueManager;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.protocol.Protocol;

public final class DbKeyGenerator implements KeyGenerator {

  private static final long INITIAL_VALUE = 0;

  private static final String LATEST_KEY = "latestKey";

  private final long keyStartValue;
  private final NextValueManager nextValueManager;

  /**
   * Initializes the key state with the corresponding partition id, so that unique keys are
   * generated over all partitions.
   *
   * @param partitionId the partition to determine the key start value
   * @param dbContext
   */
  public DbKeyGenerator(final int partitionId, final ZeebeDb zeebeDb, final DbContext dbContext) {
    keyStartValue = Protocol.encodePartitionId(partitionId, INITIAL_VALUE);
    nextValueManager =
        new NextValueManager(keyStartValue, zeebeDb, dbContext, ZbColumnFamilies.KEY);
  }

  @Override
  public long nextKey() {
    return nextValueManager.getNextValue(LATEST_KEY);
  }
}
