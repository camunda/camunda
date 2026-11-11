/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;

/**
 * Creates the key generator of a partition's stream processor, on the transaction context of its
 * state, so that a generated key commits with the changes that use it.
 */
@FunctionalInterface
public interface KeyGeneratorFactory {

  KeyGeneratorControls create(
      int partitionId, ZeebeDb<?> zeebeDb, TransactionContext transactionContext);
}
