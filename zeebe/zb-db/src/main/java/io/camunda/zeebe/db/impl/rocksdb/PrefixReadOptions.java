/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import org.rocksdb.ReadOptions;

public class PrefixReadOptions {
  public static ReadOptions readOptions() {
    return new ReadOptions()
        .setPrefixSameAsStart(true)
        .setTotalOrderSeek(false)
        // setting a positive value to read-ahead is only useful when using network storage with
        // high latency, at the cost of making iterators more expensive (memory and computation
        // wise)
        .setReadaheadSize(0);
  }
}
