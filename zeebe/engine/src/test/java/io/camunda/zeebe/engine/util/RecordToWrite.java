/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;

public final class RecordToWrite implements LogAppendEntry {

  private static final long DEFAULT_KEY = 1;

  private final RecordMetadata recordMetadata;
  private UnifiedRecordValue unifiedRecordValue;

  private long key = DEFAULT_KEY;
  private int sourceIndex = -1;

  private RecordToWrite(final RecordMetadata recordMetadata) {
    this.recordMetadata = recordMetadata;
  }

  public static RecordToWrite command() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata.recordType(RecordType.COMMAND);
    recordMetadata.authorization(
        AuthorizationUtil.getAuthInfo(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return new RecordToWrite(recordMetadata);
  }

  public static RecordToWrite event() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.EVENT));
  }

  public static RecordToWrite rejection() {
    final RecordMetadata recordMetadata = new RecordMetadata();
    return new RecordToWrite(recordMetadata.recordType(RecordType.COMMAND_REJECTION));
  }

  public RecordToWrite job(final JobIntent intent) {
    return job(intent, new JobRecord().setType("type").setRetries(3).setWorker("worker"));
  }

  public RecordToWrite jobBatch(final JobBatchIntent intent) {
    recordMetadata.valueType(ValueType.JOB_BATCH).intent(intent);

    final JobBatchRecord jobBatchRecord =
        new JobBatchRecord()
            .setWorker("worker")
            .setTimeout(10_000L)
            .setType("type")
            .setMaxJobsToActivate(1);

    unifiedRecordValue = jobBatchRecord;
    return this;
  }

  public RecordToWrite job(final JobIntent intent, final JobRecordValue value) {
    recordMetadata.valueType(ValueType.JOB).intent(intent);
    unifiedRecordValue = (JobRecord) value;
    return this;
  }

  public RecordToWrite message(final MessageIntent intent, final MessageRecordValue message) {
    recordMetadata.valueType(ValueType.MESSAGE).intent(intent);
    unifiedRecordValue = (MessageRecord) message;
    return this;
  }

  public RecordToWrite processMessageSubscription(
      final ProcessMessageSubscriptionIntent intent,
      final ProcessMessageSubscriptionRecordValue message) {
    recordMetadata.valueType(ValueType.PROCESS_MESSAGE_SUBSCRIPTION).intent(intent);
    unifiedRecordValue = (ProcessMessageSubscriptionRecord) message;
    return this;
  }

  public RecordToWrite timer(final TimerIntent intent, final TimerRecordValue value) {
    recordMetadata.valueType(ValueType.TIMER).intent(intent);
    unifiedRecordValue = (TimerRecord) value;
    return this;
  }

  public RecordToWrite processInstance(
      final ProcessInstanceIntent intent, final ProcessInstanceRecordValue value) {
    recordMetadata.valueType(ValueType.PROCESS_INSTANCE).intent(intent);
    unifiedRecordValue = (ProcessInstanceRecord) value;
    return this;
  }

  public RecordToWrite processInstanceCreation(
      final ProcessInstanceCreationIntent intent, final ProcessInstanceCreationRecordValue value) {
    recordMetadata.valueType(ValueType.PROCESS_INSTANCE_CREATION).intent(intent);
    unifiedRecordValue = (ProcessInstanceCreationRecord) value;
    return this;
  }

  public RecordToWrite processInstanceBatch(
      final ProcessInstanceBatchIntent intent, final ProcessInstanceBatchRecordValue value) {
    recordMetadata.valueType(ValueType.PROCESS_INSTANCE_BATCH).intent(intent);
    unifiedRecordValue = (ProcessInstanceBatchRecord) value;
    return this;
  }

  public RecordToWrite variable(
      final VariableDocumentIntent intent, final VariableDocumentRecordValue value) {
    recordMetadata.valueType(ValueType.VARIABLE_DOCUMENT).intent(intent);
    unifiedRecordValue = (VariableDocumentRecord) value;
    return this;
  }

  public RecordToWrite modification(final ProcessInstanceModificationRecordValue value) {
    recordMetadata
        .valueType(ValueType.PROCESS_INSTANCE_MODIFICATION)
        .intent(ProcessInstanceModificationIntent.MODIFY);
    unifiedRecordValue = (ProcessInstanceModificationRecord) value;
    return this;
  }

  public RecordToWrite migration(final ProcessInstanceMigrationRecordValue value) {
    recordMetadata
        .valueType(ValueType.PROCESS_INSTANCE_MIGRATION)
        .intent(ProcessInstanceMigrationIntent.MIGRATE);
    unifiedRecordValue = (ProcessInstanceMigrationRecord) value;
    return this;
  }

  public RecordToWrite messageBatch(final MessageBatchRecordValue value) {
    recordMetadata.valueType(ValueType.MESSAGE_BATCH).intent(MessageBatchIntent.EXPIRE);
    unifiedRecordValue = (MessageBatchRecord) value;
    return this;
  }

  public RecordToWrite scale(final ScaleIntent intent, final ScaleRecord value) {
    recordMetadata.valueType(ValueType.SCALE).intent(intent);
    unifiedRecordValue = value;
    return this;
  }

  public RecordToWrite adHocSubProcessInstruction(
      final AdHocSubProcessInstructionIntent intent,
      final AdHocSubProcessInstructionRecordValue value) {
    recordMetadata.valueType(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION).intent(intent);
    unifiedRecordValue = (AdHocSubProcessInstructionRecord) value;
    return this;
  }

  /**
   * Used to refer to the record that caused this record to be written. For example, when you want
   * to write a Job Created event that was the result of the processing of a Service Task
   * Activate_Element command. When using this with {@link
   * EngineRule#writeRecords(RecordToWrite...)} or {@link
   * StreamProcessorRule#writeBatch(RecordToWrite...)} the writer will set the index as the source
   * index of this record. In addition, the source record position of this record will be set to the
   * position of the referenced record.
   *
   * <pre>
   *   ENGINE.writeRecords(
   *     RecordToWrite.command().job(COMPLETE, job),
   *     RecordToWrite.event().causedBy(0).job(COMPLETED, job),
   *     RecordToWrite.command().causedBy(0).processInstance(COMPLETE_ELEMENT, task),
   *     RecordToWrite.event().causedBy(2).processInstance(ELEMENT_COMPLETING, task),
   *     RecordToWrite.event().causedBy(2).processInstance(ELEMENT_COMPLETED, task),
   *     RecordToWrite.event().causedBy(2).processInstance(SEQUENCE_FLOW_TAKEN, flow),
   *     RecordToWrite.command().causedBy(2).processInstance(ACTIVATE_ELEMENT, end),
   *     RecordToWrite.event().causedBy(6).processInstance(ELEMENT_ACTIVATING, end),
   *     ...
   *   );
   * </pre>
   *
   * @param index The index in the batch of the source record, also known as sourceIndex of this.
   * @return this
   */
  public RecordToWrite causedBy(final int index) {
    sourceIndex = index;
    return this;
  }

  public RecordToWrite key(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public long key() {
    return key;
  }

  @Override
  public int sourceIndex() {
    return sourceIndex;
  }

  @Override
  public RecordMetadata recordMetadata() {
    return recordMetadata;
  }

  @Override
  public UnifiedRecordValue recordValue() {
    return unifiedRecordValue;
  }
}
