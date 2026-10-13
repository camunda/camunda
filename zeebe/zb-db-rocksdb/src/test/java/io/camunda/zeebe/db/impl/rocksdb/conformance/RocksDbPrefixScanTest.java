/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.conformance;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.AbstractPrefixScanTest;
import io.camunda.zeebe.db.impl.rocksdb.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;

public final class RocksDbPrefixScanTest extends AbstractPrefixScanTest {

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return DefaultZeebeDbFactory.getDefaultFactory();
  }
}
