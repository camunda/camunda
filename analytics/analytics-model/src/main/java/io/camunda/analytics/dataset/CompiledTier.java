/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.eventbridge.streaming.window.Windows;

/**
 * One window tier of a cube (ADR 0009): its tumbling window size, and the stable {@code cellGroup}
 * that prefixes this tier's durable cells in the shared cell store. Tiers are a property of the
 * <em>cube</em>, not of a meter — every meter materialises at every tier, inside the composite
 * accumulator. Only the finest tier is aggregated in Stage 1 and shuffled; Stage 2 rolls each
 * composite delta up into every tier (a coarser cell is the exact slot-wise merge of its finer
 * deltas).
 */
public record CompiledTier(long windowMs, int cellGroup, Windows windows) {}
