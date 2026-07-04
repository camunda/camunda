/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

import org.apache.kafka.streams.kstream.ForeachAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sink stub — Stage C's output goes here.
 *
 * <p>In a real deployment this would upsert into the serving store (RDBMS / ES / OS). Here it just
 * logs, because the point of the example is the topology, not the store. It is a plain {@link
 * ForeachAction} so it plugs straight into {@code KStream.foreach(...)}.
 *
 * <p>Important: under {@code exactly_once_v2} the framework's transactional guarantee covers Kafka
 * topics and the offsets/state it manages — it does NOT cover this external side effect. Writing to an
 * external store exactly-once is on you (idempotent upsert keyed by (processId, windowStartMs), which
 * {@link Cell} conveniently is).
 */
public final class ServingSink implements ForeachAction<String, Cell> {

  private static final Logger LOG = LoggerFactory.getLogger(ServingSink.class);

  @Override
  public void apply(final String windowKey, final Cell cell) {
    LOG.info(
        "SERVE cell[{}] window={} count={} avgDurationMs={}",
        cell.processId(),
        cell.windowStartMs(),
        cell.count(),
        String.format("%.1f", cell.avgDurationMs()));
  }
}
