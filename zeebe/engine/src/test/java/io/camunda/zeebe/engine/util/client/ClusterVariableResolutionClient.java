/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.clustervariableresolver.ClusterVariableResolverRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableResolverIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableResolverRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.LongFunction;

public final class ClusterVariableResolutionClient {

  private static final long DEFAULT_KEY = -1;

  private static final LongFunction<Record<ClusterVariableResolverRecordValue>> SUCCESS_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.clusterVariableResolutionRecords()
              .onlyEvents()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();
  private static final LongFunction<Record<ClusterVariableResolverRecordValue>> REJECTION_SUPPLIER =
      (sourceRecordPosition) ->
          RecordingExporter.clusterVariableResolutionRecords()
              .onlyCommandRejections()
              .withSourceRecordPosition(sourceRecordPosition)
              .getFirst();

  private final long requestId = new Random().nextLong();
  private final int requestStreamId = new Random().nextInt();

  private final ClusterVariableResolverRecord clusterVariableResolverRecord;
  private final CommandWriter writer;
  private LongFunction<Record<ClusterVariableResolverRecordValue>> expectation = SUCCESS_SUPPLIER;

  public ClusterVariableResolutionClient(final CommandWriter writer) {
    clusterVariableResolverRecord = new ClusterVariableResolverRecord();
    this.writer = writer;
  }

  public ClusterVariableResolutionClient withReference(final String reference) {
    clusterVariableResolverRecord.setReference(reference);
    return this;
  }

  public ClusterVariableResolutionClient withTenantId(final String tenantId) {
    clusterVariableResolverRecord.setTenantId(tenantId);
    return this;
  }

  public ClusterVariableResolutionClient expectRejection() {
    expectation = REJECTION_SUPPLIER;
    return this;
  }

  public Record<ClusterVariableResolverRecordValue> resolve() {
    final long position =
        writer.writeCommand(
            DEFAULT_KEY,
            requestStreamId,
            requestId,
            ClusterVariableResolverIntent.RESOLVE,
            clusterVariableResolverRecord);
    return expectation.apply(position);
  }
}
