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
 * A {@link ZeebeDb} that also exposes its committed state at byte level through {@link
 * #rawAccess()}, so that a layer such as a write-behind cache can read from and write to it
 * directly.
 */
public interface RawAccessibleDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    extends ZeebeDb<ColumnFamilyType> {

  /** Direct access to the committed state, bypassing transactions. */
  RawDbAccess rawAccess();

  /** The consistency checks that this DB runs, which a layer on top of it must run as well. */
  ConsistencyChecksSettings consistencyChecksSettings();
}
