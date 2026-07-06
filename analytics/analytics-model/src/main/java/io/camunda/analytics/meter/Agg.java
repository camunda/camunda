/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

/**
 * The aggregate a store applies to a {@link PushdownColumn} when it rolls up additive cells in the
 * engine ({@code GROUP BY} + {@code SUM}/{@code MIN}/{@code MAX} on RDBMS, the matching {@code
 * sum}/{@code min}/{@code max} sub-aggregation on ES/OS). Only the operators an additive
 * accumulator needs: a counter/total is {@code SUM}, a running min/max is {@code MIN}/{@code MAX}.
 */
public enum Agg {
  SUM,
  MIN,
  MAX
}
