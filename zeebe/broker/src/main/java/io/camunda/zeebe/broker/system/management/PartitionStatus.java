/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import java.time.Instant;

public record PartitionStatus(
    Role role,
    Long processedPosition,
    String snapshotId,
    Long processedPositionInSnapshot,
    Phase streamProcessorPhase,
    ExporterPhase exporterPhase,
    Long exportedPosition,
    ClockStatus clock,
    HealthTree health) {
  // without the modificationType, you need to interpret the modification based on its fields, which
  // may not always be obvious
  public record ClockStatus(Instant instant, String modificationType, Modification modification) {}
}
