/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class Records {

  public static DeploymentRecord asDeploymentRecord(final LoggedEvent event) {
    return readValueAs(event, DeploymentRecord.class);
  }

  public static JobRecord asJobRecord(final LoggedEvent event) {
    return readValueAs(event, JobRecord.class);
  }

  protected static <T extends UnpackedObject> T readValueAs(
      final LoggedEvent event, final Class<T> valueClass) {
    final DirectBuffer copy =
        BufferUtil.cloneBuffer(
            event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
    final T valuePojo = ReflectUtil.newInstance(valueClass);
    valuePojo.wrap(copy);
    return valuePojo;
  }

  public static boolean isDeploymentRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.DEPLOYMENT);
  }

  public static boolean isJobRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.JOB);
  }

  public static boolean isIncidentRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.INCIDENT);
  }

  public static boolean isProcessInstanceRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.PROCESS_INSTANCE);
  }

  public static boolean isMessageRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE);
  }

  public static boolean isMessageSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE_SUBSCRIPTION);
  }

  public static boolean isMessageStartEventSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  public static boolean isProcessMessageSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
  }

  public static boolean isTimerRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.TIMER);
  }

  public static boolean isProcessInstanceCreationRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.PROCESS_INSTANCE_CREATION);
  }

  public static boolean isErrorRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.ERROR);
  }

  public static boolean hasIntent(final LoggedEvent event, final Intent intent) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getIntent() == intent;
  }

  private static RecordMetadata getMetadata(final LoggedEvent event) {
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);

    return metadata;
  }

  public static boolean isRejection(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.COMMAND_REJECTION;
  }

  public static boolean isRejection(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isRejection(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isEvent(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.EVENT;
  }

  public static boolean isEvent(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isEvent(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isCommand(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.COMMAND;
  }

  public static boolean isCommand(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isCommand(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isRecordOfType(final LoggedEvent event, final ValueType type) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getValueType() == type;
  }

  public static ProcessRecord process(final long processKey, final String processId) {
    final var record = new ProcessRecord();
    record
        .setKey(processKey)
        .setBpmnProcessId(processId)
        .setResourceName("process.bpmn")
        .setResource(
            BufferUtil.wrapString(
                Bpmn.convertToString(
                    Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())))
        .setVersion(1)
        .setChecksum(BufferUtil.wrapString("checksum"));
    return record;
  }

  public static ProcessInstanceRecord processInstance(final long instanceKey) {
    return processInstance(instanceKey, "processId");
  }

  public static ProcessInstanceRecord processInstance(
      final long instanceKey, final String processId) {
    final var record = new ProcessInstanceRecord();
    record.setProcessInstanceKey(instanceKey);
    record.setBpmnProcessId(processId);
    return record;
  }

  public static ErrorRecord error(final int instanceKey, final long pos) {
    final ErrorRecord event = new ErrorRecord();
    event.initErrorRecord(new Exception("expected"), pos);
    event.setProcessInstanceKey(instanceKey);
    return event;
  }

  public static JobRecord job(final long instanceKey) {
    return job(instanceKey, "processId");
  }

  public static JobRecord job(final long instanceKey, final String processId) {
    final JobRecord record = new JobRecord();
    record.setProcessInstanceKey(instanceKey);
    record.setType("test");
    record.setBpmnProcessId(processId);
    return record;
  }

  public static TimerRecord timer(final long instanceKey) {
    final TimerRecord event = new TimerRecord();
    event
        .setProcessInstanceKey(instanceKey)
        .setElementInstanceKey(instanceKey)
        .setDueDate(1245)
        .setTargetElementId(BufferUtil.wrapString("foo"))
        .setRepetitions(0)
        .setProcessDefinitionKey(1);
    return event;
  }

  public static VariableDocumentRecord variableDocument(
      final long instanceKey, final String variables) {
    return new VariableDocumentRecord()
        .setScopeKey(instanceKey)
        .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)));
  }

  public static <T extends UnpackedObject & RecordValue> T cloneValue(final RecordValue value) {
    final UnpackedObject unpackedValue = (UnpackedObject) value;
    final MutableDirectBuffer buffer =
        new UnsafeBuffer(ByteBuffer.allocate(unpackedValue.getLength()));
    final T cloned = (T) ReflectUtil.newInstance(value.getClass());

    unpackedValue.write(buffer, 0);
    cloned.wrap(buffer, 0, unpackedValue.getLength());

    return cloned;
  }
}
