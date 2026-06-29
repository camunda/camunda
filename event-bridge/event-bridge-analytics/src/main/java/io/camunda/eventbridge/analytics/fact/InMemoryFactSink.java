/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

import java.util.ArrayList;
import java.util.List;

/** A {@link FactSink} that records published facts in memory, for tests. */
public final class InMemoryFactSink implements FactSink {

  private final List<ProcessInstanceExecutionTimeFact> published = new ArrayList<>();

  @Override
  public void publish(final ProcessInstanceExecutionTimeFact fact) {
    published.add(fact);
  }

  public List<ProcessInstanceExecutionTimeFact> published() {
    return List.copyOf(published);
  }
}
