/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import java.util.List;
import java.util.Map;

/**
 * One periodic-snapshot row read back from a cube's {@code _snapshots} table (ADR 0010): the key's
 * dimension values, the event-time boundary, and every meter's <em>cumulative absolute</em> value
 * as of that boundary, recomposed to the meter's read-facing result. Snapshot points are sparse — a
 * key emits one only when its value changed — so a series read carries the last point forward
 * between boundaries.
 */
public record SnapshotPoint(List<Object> keyValues, long sampleTime, Map<String, Object> measures) {

  public SnapshotPoint {
    keyValues = List.copyOf(keyValues);
    measures = Map.copyOf(measures);
  }
}
