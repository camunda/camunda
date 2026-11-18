/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

/** What the tests need from every variant of the big record, legacy or generated. */
public interface BigRecord {

  /** Sets every property except {@code skipped}, which stays unset. */
  void fillExcept(String skipped);

  long getKey();

  int getPartitionId();
}
