/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.meter;

import io.camunda.analytics.dimension.DimensionType;

/**
 * One native numeric column a pushable meter decomposes into (see {@link PushdownSpec}). The {@code
 * suffix} discriminates the column within its meter — a store forms the physical column name from
 * the meter name plus this suffix, so an empty suffix is a single-column meter (e.g. {@code count})
 * and {@code matched}/{@code total} are the two columns of a ratio. {@code type} is the column's
 * storage type; {@code agg} is the operator the store applies when rolling the column up.
 *
 * @param suffix the per-meter column discriminator ({@code ""} for a single-column meter)
 * @param type the column's storage type
 * @param agg the aggregate the store applies when reducing this column
 */
public record PushdownColumn(String suffix, DimensionType type, Agg agg) {}
