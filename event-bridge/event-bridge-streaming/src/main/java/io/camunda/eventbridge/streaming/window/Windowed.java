/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.window;

/**
 * A grouping key scoped to a window: the base key plus the start of the event-time window it falls
 * into. Produced by {@code Grouped.windowedBy(...)}, it is the key a windowed aggregate is stored
 * under — so {@code (region, hour)} cells are independent rows.
 *
 * @param <K> the base key type
 */
public record Windowed<K>(K key, long windowStart) {}
