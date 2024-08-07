/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.property.db;

import io.camunda.zeebe.db.DbValue;

interface InMemoryDbState {
  void put(FullyQualifiedKey fullyQualifiedKey, DbValue value);

  byte[] get(FullyQualifiedKey fullyQualifiedKey);

  void delete(FullyQualifiedKey fullyQualifiedKey);

  InMemoryDbIterator newIterator();

  boolean contains(FullyQualifiedKey fullyQualifiedKey);
}
