/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;

final class InMemoryDbIterator {

  private final SortedMap<Bytes, Bytes> database;

  InMemoryDbIterator(final SortedMap<Bytes, Bytes> database) {
    this.database = database;
  }

  InMemoryDbIterator seek(final byte[] prefixedKey, final int prefixLength) {
    return new InMemoryDbIterator(database.tailMap(Bytes.fromByteArray(prefixedKey, prefixLength)));
  }

  Iterator<Map.Entry<Bytes, Bytes>> iterate() {
    return database.entrySet().iterator();
  }
}
