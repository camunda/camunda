/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import java.util.HashMap;
import java.util.Map;

final class CfCache {
  final Map<byte[], byte[]> cache = new HashMap<>();

  void add(byte[] key, byte[] value) {
    cache.put(key, value);
  }

  byte[] get(byte[] key) {
    return cache.get(key);
  }

  CacheState test(byte[] key) {
    if (cache.containsKey(key)) {
      if (cache.get(key) == null) {
        return CacheState.DoesNotExist;
      } else {
        return CacheState.Cached;
      }
    } else {
      return CacheState.Unknown;
    }
  }

  enum CacheState {
    Cached,
    Unknown,
    DoesNotExist,
  }
}
