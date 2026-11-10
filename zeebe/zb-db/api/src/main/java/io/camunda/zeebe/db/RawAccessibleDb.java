/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.protocol.EnumValue;

/**
 * A {@link ZeebeDb} that exposes its committed state at byte level, and whose column families work
 * over any {@link ZeebeTransaction} that a {@link TransactionContext} returns. Both together let a
 * layer such as a write-behind cache provide its own transactions on top of it.
 */
public interface RawAccessibleDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    extends ZeebeDb<ColumnFamilyType> {

  /** Direct access to the committed state, bypassing transactions. */
  RawDbAccess rawAccess();
}
