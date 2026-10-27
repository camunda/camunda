/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class ScaleClient {

  private final CommandWriter writer;

  public ScaleClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ScaleUpClient scaleUp() {
    return new ScaleUpClient(writer);
  }

  public MarkPartitionBootstrappedClient markPartitionBootstrapped() {
    return new MarkPartitionBootstrappedClient(writer);
  }

  public static class ScaleUpClient {

    private static final Function<Long, Record<ScaleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.scaleRecords()
                .withIntent(ScaleIntent.SCALING_UP)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<ScaleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.scaleRecords()
                .onlyCommandRejections()
                .withIntent(ScaleIntent.SCALE_UP)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final ScaleRecord scaleRecord;
    private Function<Long, Record<ScaleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public ScaleUpClient(final CommandWriter writer) {
      this.writer = writer;
      scaleRecord = new ScaleRecord();
    }

    public Record<ScaleRecordValue> scaleUp(final int desiredPartitionCount) {
      final long position =
          writer.writeCommand(ScaleIntent.SCALE_UP, scaleRecord.scaleUp(desiredPartitionCount));
      return expectation.apply(position);
    }

    public ScaleUpClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }

  public static class MarkPartitionBootstrappedClient {

    private static final Function<Long, Record<ScaleRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.scaleRecords()
                .withIntent(ScaleIntent.PARTITION_BOOTSTRAPPED)
                .withSourceRecordPosition(position)
                .getFirst();

    private static final Function<Long, Record<ScaleRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.scaleRecords()
                .onlyCommandRejections()
                .withIntent(ScaleIntent.MARK_PARTITION_BOOTSTRAPPED)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final ScaleRecord scaleRecord;
    private Function<Long, Record<ScaleRecordValue>> expectation = SUCCESS_SUPPLIER;

    public MarkPartitionBootstrappedClient(final CommandWriter writer) {
      this.writer = writer;
      scaleRecord = new ScaleRecord();
    }

    public Record<ScaleRecordValue> markBootstrapped(final int partitionId) {
      final long position =
          writer.writeCommand(
              ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
              scaleRecord.markPartitionBootstrapped(partitionId));
      return expectation.apply(position);
    }

    public MarkPartitionBootstrappedClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }
  }
}
