/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.conformance;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.AbstractDbStringColumnFamilyTest;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.DefaultZeebeDbFactory;

public final class RocksDbDbStringColumnFamilyTest extends AbstractDbStringColumnFamilyTest {

  @Override
  protected ZeebeDbFactory<DefaultColumnFamily> factory() {
    return DefaultZeebeDbFactory.getDefaultFactory();
  }
}
