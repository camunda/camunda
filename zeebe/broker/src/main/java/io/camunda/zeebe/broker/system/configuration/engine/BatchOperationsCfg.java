/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public class BatchOperationsCfg implements ConfigurationEntry {

  private int blockSize = EngineConfiguration.DEFAULT_BATCH_OPERATION_BLOCK_SIZE;

  public int getBlockSize() {
    return blockSize;
  }

  public void setBlockSize(final int blockSize) {
    this.blockSize = blockSize;
  }

  @Override
  public String toString() {
    return "BatchOperationsCfg{" + "blockSize=" + blockSize + '}';
  }
}
