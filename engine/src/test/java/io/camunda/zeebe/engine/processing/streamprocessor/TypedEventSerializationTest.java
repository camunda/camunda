/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.streamprocessor.TypedRecordImpl;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.util.collection.Tuple;
import org.agrona.DirectBuffer;
import org.junit.Test;

public final class TypedEventSerializationTest {

  private static Tuple<TypedRecord, CopiedRecord> createRecordTuple() {
    final RecordMetadata recordMetadata = new RecordMetadata();

    final DeploymentIntent intent = DeploymentIntent.CREATE;
    final int protocolVersion = 1;
    final ValueType valueType = ValueType.DEPLOYMENT;

    final RecordType recordType = RecordType.COMMAND;
    final String rejectionReason = "fails";
    final RejectionType rejectionType = RejectionType.INVALID_ARGUMENT;
    final int requestId = 23;
    final int requestStreamId = 1;

    recordMetadata
        .intent(intent)
        .protocolVersion(protocolVersion)
        .valueType(valueType)
        .recordType(recordType)
        .rejectionReason(rejectionReason)
        .rejectionType(rejectionType)
        .requestId(requestId)
        .requestStreamId(requestStreamId);

    final String resourceName = "resource";
    final DirectBuffer resource = wrapString("contents");
    final String bpmnProcessId = "testProcess";
    final long processDefinitionKey = 123;
    final int processVersion = 12;
    final DeploymentRecord record = new DeploymentRecord();
    record.resources().add().setResourceName(wrapString(resourceName)).setResource(resource);
    record
        .processesMetadata()
        .add()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setKey(processDefinitionKey)
        .setResourceName(wrapString(resourceName))
        .setVersion(processVersion)
        .setChecksum(wrapString("checksum"));

    final long key = 1234;
    final long position = 4321;
    final long sourcePosition = 231;
    final long timestamp = 2191L;

    final LoggedEvent loggedEvent = mock(LoggedEvent.class);
    when(loggedEvent.getPosition()).thenReturn(position);
    when(loggedEvent.getKey()).thenReturn(key);
    when(loggedEvent.getSourceEventPosition()).thenReturn(sourcePosition);
    when(loggedEvent.getTimestamp()).thenReturn(timestamp);

    final TypedRecordImpl typedEvent = new TypedRecordImpl(0);
    typedEvent.wrap(loggedEvent, recordMetadata, record);

    final CopiedRecord copiedRecord =
        new CopiedRecord<>(record, recordMetadata, key, 0, position, sourcePosition, timestamp);

    return new Tuple<>(typedEvent, copiedRecord);
  }

  @Test
  public void shouldCreateSameJson() {
    // given
    final Tuple<TypedRecord, CopiedRecord> records = createRecordTuple();
    final String expectedJson = records.getRight().toJson();

    // when
    final String actualJson = records.getLeft().toJson();

    // then
    JsonUtil.assertEquality(actualJson, expectedJson);
  }
}
