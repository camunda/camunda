/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * The read-facing result of {@link RatioAggregateFunction}: the matched and total counts and the
 * derived fraction in [0, 1] ({@code 0.0} when nothing was folded in).
 *
 * @param matched the number of facts satisfying the predicate
 * @param total the number of facts folded in
 * @param ratio {@code matched / total}, or {@code 0.0} when {@code total} is zero
 */
public record RatioResult(long matched, long total, double ratio) {}
