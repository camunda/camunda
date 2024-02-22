/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.management;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;

public record PartitionStatus(
    Role role,
    Long processedPosition,
    String snapshotId,
    Long processedPositionInSnapshot,
    Phase streamProcessorPhase,
    ExporterPhase exporterPhase,
    Long exportedPosition) {}
