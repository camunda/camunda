/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.stream.IntStream;

/** Seeds the state a follower that rejected a distributed process deletion leaves behind. */
final class OrphanedProcessDefinitions {

  private OrphanedProcessDefinitions() {}

  /**
   * Leaves the definition deleted on every partition but the {@code stuckPartitions}, where it
   * rests in PENDING_DELETION, and the deployment partition still awaiting their drain reports.
   */
  static void orphanOnPartitions(
      final EngineRule engine,
      final ProcessMetadataValue metadata,
      final int partitionCount,
      final int... stuckPartitions) {
    final var allPartitions = IntStream.rangeClosed(1, partitionCount).boxed().toList();
    final var stuck = IntStream.of(stuckPartitions).boxed().toList();

    engine.stop();
    for (final int partitionId : allPartitions) {
      if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
        continue;
      }
      if (stuck.contains(partitionId)) {
        engine.writeRecordsOnPartition(partitionId, processEvent(ProcessIntent.DELETING, metadata));
      } else {
        engine.writeRecordsOnPartition(
            partitionId,
            processEvent(ProcessIntent.DELETING, metadata),
            processEvent(ProcessIntent.DELETED, metadata));
      }
    }
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DRAINING, processRecord(metadata).setDrainPartitions(allPartitions)),
        processEvent(ProcessIntent.DELETED, metadata));
    engine.start();

    engine.writeRecords(
        allPartitions.stream()
            .filter(partitionId -> !stuck.contains(partitionId))
            .map(partitionId -> drainReport(metadata, partitionId))
            .toArray(RecordToWrite[]::new));
    // wait until all non-stuck partitions completed their deletion
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETE_COMPLETED)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
        .limit(partitionCount - stuck.size())
        .toList();
  }

  static void assertFullyDeleted(final long processDefinitionKey) {
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                .exists())
        .describedAs("definition %d is fully deleted", processDefinitionKey)
        .isTrue();
  }

  private static RecordToWrite processEvent(
      final ProcessIntent intent, final ProcessMetadataValue metadata) {
    return RecordToWrite.event()
        .key(metadata.getProcessDefinitionKey())
        .process(intent, processRecord(metadata));
  }

  private static RecordToWrite drainReport(
      final ProcessMetadataValue metadata, final int reportingPartitionId) {
    // the processor decodes the reporting partition from the report key
    final long reportKey =
        Protocol.encodePartitionId(
            reportingPartitionId,
            Protocol.decodeKeyInPartition(metadata.getProcessDefinitionKey()));
    return RecordToWrite.command()
        .key(reportKey)
        .process(ProcessIntent.DELETE_COMPLETE, processRecord(metadata));
  }

  private static ProcessRecord processRecord(final ProcessMetadataValue metadata) {
    return new ProcessRecord()
        .setKey(metadata.getProcessDefinitionKey())
        .setBpmnProcessId(metadata.getBpmnProcessId())
        .setVersion(metadata.getVersion())
        .setResourceName(metadata.getResourceName())
        .setTenantId(metadata.getTenantId());
  }
}
