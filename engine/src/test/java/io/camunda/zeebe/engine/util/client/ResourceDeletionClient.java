/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public class ResourceDeletionClient {

  private static final Function<Long, Record<ResourceDeletionRecordValue>> SUCCESS_EXPECTATION =
      (position) ->
          RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETED)
              .withSourceRecordPosition(position)
              .getFirst();
  private static final Function<Long, Record<ResourceDeletionRecordValue>> REJECTION_EXPECTATION =
      (position) ->
          RecordingExporter.resourceDeletionRecords()
              .onlyCommandRejections()
              .withIntent(ResourceDeletionIntent.DELETE)
              .withSourceRecordPosition(position)
              .getFirst();

  private final CommandWriter writer;
  private final ResourceDeletionRecord resourceDeletionRecord = new ResourceDeletionRecord();
  private Function<Long, Record<ResourceDeletionRecordValue>> expectation = SUCCESS_EXPECTATION;

  public ResourceDeletionClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ResourceDeletionClient withResourceKey(final long resourceKey) {
    resourceDeletionRecord.setResourceKey(resourceKey);
    return this;
  }

  public Record<ResourceDeletionRecordValue> delete() {
    final long position =
        writer.writeCommand(ResourceDeletionIntent.DELETE, resourceDeletionRecord);
    return expectation.apply(position);
  }

  public ResourceDeletionClient expectRejection() {
    expectation = REJECTION_EXPECTATION;
    return this;
  }
}
