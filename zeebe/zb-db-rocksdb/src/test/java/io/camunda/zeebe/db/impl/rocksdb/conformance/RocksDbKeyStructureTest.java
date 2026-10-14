/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.conformance;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.conformance.AbstractKeyStructureTest;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;

public final class RocksDbKeyStructureTest extends AbstractKeyStructureTest {

  @Override
  protected DbKey emptyKey() {
    return DbNullKey.INSTANCE;
  }
}
