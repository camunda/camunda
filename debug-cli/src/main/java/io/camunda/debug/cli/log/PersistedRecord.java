/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

// Java equivalent of the Kotlin interface io.zell.zdb.log.records.PersistedRecord
public interface PersistedRecord {
  long index();

  long term();

  @Override
  String toString();

  String asColumnString();
}
