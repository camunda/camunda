/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder.TEMPLATE_ID;

import io.camunda.zeebe.broker.transport.ApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.RequestReaderException;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;
import org.agrona.DirectBuffer;

public class CommandApiRequestReader implements RequestReader<ExecuteCommandRequestDecoder> {
  static final Map<ValueType, UnpackedObject> RECORDS_BY_TYPE = new EnumMap<>(ValueType.class);

  static {
    RECORDS_BY_TYPE.put(ValueType.DEPLOYMENT, new DeploymentRecord());
    RECORDS_BY_TYPE.put(ValueType.JOB, new JobRecord());
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE, new ProcessInstanceRecord());
    RECORDS_BY_TYPE.put(ValueType.MESSAGE, new MessageRecord());
    RECORDS_BY_TYPE.put(ValueType.JOB_BATCH, new JobBatchRecord());
    RECORDS_BY_TYPE.put(ValueType.INCIDENT, new IncidentRecord());
    RECORDS_BY_TYPE.put(ValueType.VARIABLE_DOCUMENT, new VariableDocumentRecord());
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE_CREATION, new ProcessInstanceCreationRecord());
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE_BATCH, new ProcessInstanceBatchRecord());
  }

  private UnpackedObject event;
  private final RecordMetadata eventMetadata = new RecordMetadata();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandRequestDecoder commandRequestDecoder =
      new ExecuteCommandRequestDecoder();

  @Override
  public void reset() {
    if (event != null) {
      event.reset();
    }
    eventMetadata.reset();
  }

  @Override
  public ExecuteCommandRequestDecoder getMessageDecoder() {
    return commandRequestDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    if (TEMPLATE_ID != templateId) {
      throw new RequestReaderException.InvalidTemplateException(
          messageHeaderDecoder.templateId(), templateId);
    }

    commandRequestDecoder.wrap(
        buffer,
        offset + MessageHeaderDecoder.ENCODED_LENGTH,
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final int eventOffset =
        commandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
    final int eventLength = commandRequestDecoder.valueLength();
    eventMetadata.protocolVersion(messageHeaderDecoder.version());
    event = RECORDS_BY_TYPE.get(commandRequestDecoder.valueType());
    if (event != null) {
      event.wrap(buffer, eventOffset, eventLength);
    }
  }

  public UnpackedObject event() {
    return event;
  }

  public RecordMetadata metadata() {
    return eventMetadata;
  }
}
