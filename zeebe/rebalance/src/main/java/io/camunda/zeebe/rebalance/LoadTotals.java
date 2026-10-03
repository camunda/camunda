/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import java.util.Map;

/**
 * A broker's running totals of each measure at one point in time.
 *
 * @param incarnation changes whenever the broker restarts, which resets its totals
 */
record LoadTotals(long incarnation, Map<LoadMeasure, Long> totals) {
  LoadTotals {
    totals = Map.copyOf(totals);
  }
}
