/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Records events that are not a status change of the health tree, such as a leader change. */
@FunctionalInterface
public interface HealthEvents {

  /**
   * Records an event. Thread safe.
   *
   * @param component the health tree component the event belongs to, relative to the broker's own
   *     node, for example {@code Partition-default-1}; empty for the broker itself
   * @param type what happened, in upper snake case, for example {@code BECAME_LEADER}
   * @param message a sentence for humans, or null
   * @param details machine-readable facts about the event
   */
  void record(String component, String type, @Nullable String message, Map<String, String> details);
}
