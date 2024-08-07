/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property.db;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.SortedMap;

final class InMemoryDbIterator {

  private final SortedMap<Bytes, Bytes> database;

  InMemoryDbIterator(final SortedMap<Bytes, Bytes> database) {
    this.database = database;
  }

  InMemoryDbIterator seek(final byte[] prefixedKey, final int prefixLength) {
    return new InMemoryDbIterator(database.tailMap(Bytes.fromByteArray(prefixedKey, prefixLength)));
  }

  Iterator<Entry<Bytes, Bytes>> iterate() {
    return database.entrySet().iterator();
  }
}
