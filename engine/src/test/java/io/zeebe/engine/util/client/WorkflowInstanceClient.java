/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.client;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class WorkflowInstanceClient {

  private final StreamProcessorRule environmentRule;

  public WorkflowInstanceClient(final StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
  }

  public WorkflowInstanceCreationClient ofBpmnProcessId(final String bpmnProcessId) {
    return new WorkflowInstanceCreationClient(environmentRule, bpmnProcessId);
  }

  public ExistingInstanceClient withInstanceKey(final long workflowInstanceKey) {
    return new ExistingInstanceClient(environmentRule, workflowInstanceKey);
  }

  public static class WorkflowInstanceCreationClient {

    private final StreamProcessorRule environmentRule;
    private final WorkflowInstanceCreationRecord workflowInstanceCreationRecord;

    public WorkflowInstanceCreationClient(
        final StreamProcessorRule environmentRule, final String bpmnProcessId) {
      this.environmentRule = environmentRule;
      workflowInstanceCreationRecord = new WorkflowInstanceCreationRecord();
      workflowInstanceCreationRecord.setBpmnProcessId(bpmnProcessId);
    }

    public WorkflowInstanceCreationClient withVariables(final Map<String, Object> variables) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public WorkflowInstanceCreationClient withVariables(final String variables) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public WorkflowInstanceCreationClient withVariable(final String key, final Object value) {
      workflowInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
      return this;
    }

    public WorkflowInstanceCreationWithResultClient withResult() {
      return new WorkflowInstanceCreationWithResultClient(
          environmentRule, workflowInstanceCreationRecord);
    }

    public long create() {
      final long position =
          environmentRule.writeCommand(
              WorkflowInstanceCreationIntent.CREATE, workflowInstanceCreationRecord);

      return RecordingExporter.workflowInstanceCreationRecords()
          .withIntent(WorkflowInstanceCreationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getWorkflowInstanceKey();
    }
  }

  public static class WorkflowInstanceCreationWithResultClient {
    private final StreamProcessorRule environmentRule;
    private final WorkflowInstanceCreationRecord record;
    private long requestId = 1L;
    private int requestStreamId = 1;

    public WorkflowInstanceCreationWithResultClient(
        final StreamProcessorRule environmentRule, final WorkflowInstanceCreationRecord record) {
      this.environmentRule = environmentRule;
      this.record = record;
    }

    public WorkflowInstanceCreationWithResultClient withFetchVariables(
        final Set<String> fetchVariables) {
      final ArrayProperty<StringValue> variablesToCollect = record.fetchVariables();
      fetchVariables.forEach(variable -> variablesToCollect.add().wrap(wrapString(variable)));
      return this;
    }

    public WorkflowInstanceCreationWithResultClient withRequestId(final long requestId) {
      this.requestId = requestId;
      return this;
    }

    public WorkflowInstanceCreationWithResultClient withRequestStreamId(final int requestStreamId) {
      this.requestStreamId = requestStreamId;
      return this;
    }

    public long create() {
      final long position =
          environmentRule.writeCommand(
              requestStreamId,
              requestId,
              WorkflowInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
              record);

      return RecordingExporter.workflowInstanceCreationRecords()
          .withIntent(WorkflowInstanceCreationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getWorkflowInstanceKey();
    }

    public void asyncCreate() {
      environmentRule.writeCommand(
          requestStreamId,
          requestId,
          WorkflowInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
          record);
    }
  }

  public static class ExistingInstanceClient {

    public static final Function<Long, Record<WorkflowInstanceRecordValue>> SUCCESS_EXPECTATION =
        (workflowInstanceKey) ->
            RecordingExporter.workflowInstanceRecords()
                .withRecordKey(workflowInstanceKey)
                .withIntent(WorkflowInstanceIntent.ELEMENT_TERMINATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst();

    public static final Function<Long, Record<WorkflowInstanceRecordValue>> REJECTION_EXPECTATION =
        (workflowInstanceKey) ->
            RecordingExporter.workflowInstanceRecords()
                .onlyCommandRejections()
                .withIntent(WorkflowInstanceIntent.CANCEL)
                .withRecordKey(workflowInstanceKey)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst();

    private static final int DEFAULT_PARTITION = -1;
    private final StreamProcessorRule environmentRule;
    private final long workflowInstanceKey;

    private int partition = DEFAULT_PARTITION;
    private Function<Long, Record<WorkflowInstanceRecordValue>> expectation = SUCCESS_EXPECTATION;

    public ExistingInstanceClient(
        final StreamProcessorRule environmentRule, final long workflowInstanceKey) {
      this.environmentRule = environmentRule;
      this.workflowInstanceKey = workflowInstanceKey;
    }

    public ExistingInstanceClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public ExistingInstanceClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public Record<WorkflowInstanceRecordValue> cancel() {
      if (partition == DEFAULT_PARTITION) {
        partition =
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst()
                .getPartitionId();
      }

      environmentRule.writeCommandOnPartition(
          partition,
          workflowInstanceKey,
          WorkflowInstanceIntent.CANCEL,
          new WorkflowInstanceRecord().setWorkflowInstanceKey(workflowInstanceKey));

      return expectation.apply(workflowInstanceKey);
    }
  }
}
