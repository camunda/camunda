/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

/**
 * Functional interface used to propagate an exporter's replay request to the exporting
 * infrastructure. Implementations decide whether the requested replay position can be satisfied
 * (e.g. the required log segments still exist) and return {@code true} on success or {@code false}
 * when the replay cannot be fulfilled.
 */
@FunctionalInterface
interface ExporterReplayControl {

  /**
   * Called when an exporter requests a replay after resetting to the given position.
   *
   * @param resetPosition the exclusive lower bound for the replay, i.e. the log position that was
   *     reset and after which replay should resume ({@code fromPosition - 1} in terms of the {@link
   *     io.camunda.zeebe.exporter.api.context.Controller#requestReplay} API)
   * @return {@code true} if the replay request was accepted and the log reader was successfully
   *     seeked, {@code false} if it was rejected (e.g. because the log segment is no longer
   *     available)
   */
  boolean requestReplay(long resetPosition);
}
