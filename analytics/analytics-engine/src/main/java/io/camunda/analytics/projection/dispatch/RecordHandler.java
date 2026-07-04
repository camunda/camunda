/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.dispatch;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import java.util.function.Consumer;

/**
 * Handles one {@code (ValueType, Intent)} transition of the base projection: it orders the three
 * Model-A steps — {@code apply} (fold the record into the row via an applier), {@code derive} (read
 * the updated row and forward facts), then {@code evict} (drop terminal rows) — for its transition.
 */
@FunctionalInterface
public interface RecordHandler {

  void handle(SourceRecord source, MutableProjectionState state, Consumer<Fact> facts);
}
