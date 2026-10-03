/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/** This broker's running totals of each {@link LoadMeasure}, which only ever increase. */
@FunctionalInterface
public interface LoadCounters {
  /** Must be safe to call from any thread. */
  long total(LoadMeasure measure);
}
