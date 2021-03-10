/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import io.zeebe.engine.processing.streamprocessor.CopiedRecords;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.ProcessInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.test.util.stream.StreamWrapper;
import io.zeebe.util.buffer.BufferUtil;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;

public final class RecordStream extends StreamWrapper<LoggedEvent, RecordStream> {

  public RecordStream(final Stream<LoggedEvent> stream) {
    super(stream);
  }

  @Override
  protected RecordStream supply(final Stream<LoggedEvent> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream withIntent(final Intent intent) {
    return new RecordStream(filter(r -> Records.hasIntent(r, intent)));
  }

  public TypedRecordStream<JobRecord> onlyJobRecords() {
    return new TypedRecordStream<>(
        filter(Records::isJobRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<IncidentRecord> onlyIncidentRecords() {
    return new TypedRecordStream<>(
        filter(Records::isIncidentRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<DeploymentRecord> onlyDeploymentRecords() {
    return new TypedRecordStream<>(
        filter(Records::isDeploymentRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<ProcessInstanceRecord> onlyProcessInstanceRecords() {
    return new TypedRecordStream<>(
        filter(Records::isProcessInstanceRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageRecord> onlyMessageRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageSubscriptionRecord> onlyMessageSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<MessageStartEventSubscriptionRecord>
      onlyMessageStartEventSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isMessageStartEventSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<ProcessInstanceSubscriptionRecord>
      onlyProcessInstanceSubscriptionRecords() {
    return new TypedRecordStream<>(
        filter(Records::isProcessInstanceSubscriptionRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<TimerRecord> onlyTimerRecords() {
    return new TypedRecordStream<>(
        filter(Records::isTimerRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<ProcessInstanceCreationRecord> onlyProcessInstanceCreationRecords() {
    return new TypedRecordStream<>(
        filter(Records::isProcessInstanceCreationRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  public TypedRecordStream<ErrorRecord> onlyErrorRecords() {
    return new TypedRecordStream<>(
        filter(Records::isErrorRecord)
            .map((l) -> CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, l)));
  }

  /**
   * This method makes only sense when the stream contains only entries of one process instance and
   * the element is only instantiated once within that instance.
   */
  public Stream<ProcessInstanceIntent> onlyStatesOf(final String elementId) {
    final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

    return onlyProcessInstanceRecords()
        .onlyEvents()
        .filter(r -> elementIdBuffer.equals(r.getValue().getElementIdBuffer()))
        .map(r -> (ProcessInstanceIntent) r.getIntent());
  }
}
