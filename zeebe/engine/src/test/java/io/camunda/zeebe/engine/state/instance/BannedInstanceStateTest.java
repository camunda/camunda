/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.common.state.mutable.MutableBannedInstanceState;
import io.camunda.zeebe.engine.common.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public final class BannedInstanceStateTest {

  private MutableProcessingState processingState;
  private MutableBannedInstanceState bannedInstanceState;

  @BeforeEach
  public void setup() {
    bannedInstanceState = processingState.getBannedInstanceState();
  }

  @Test
  public void shouldBanInstanceWithKey() {
    // given
    final var processInstanceRecord = createRecord();

    // when
    bannedInstanceState.banProcessInstance(1000L);

    // then
    assertThat(bannedInstanceState.isBanned(processInstanceRecord)).isTrue();
  }

  @Test
  public void shouldBanInstanceWithRecord() {
    // given
    final var processInstanceRecord = createRecord();

    // when
    bannedInstanceState.tryToBanInstance(processInstanceRecord, (pi) -> {});

    // then
    assertThat(bannedInstanceState.isBanned(processInstanceRecord)).isTrue();
  }

  @Test
  public void shouldReturnFalseIfNotBanned() {
    // given
    final var processInstanceRecord = createRecord();

    // when - no ban

    // then
    assertThat(bannedInstanceState.isBanned(processInstanceRecord)).isFalse();
  }

  @Test
  public void shouldCallCallbackIfBanned() {
    // given
    final var processInstanceRecord = createRecord();
    final var consumer = mock(Consumer.class);

    // when - no banning
    bannedInstanceState.tryToBanInstance(processInstanceRecord, consumer);

    // then
    assertThat(bannedInstanceState.isBanned(processInstanceRecord)).isTrue();
    verify(consumer, times(1)).accept(1000L);
  }

  @Test
  public void shouldNotCallCallbackIfNotProcessInstanceIntent() {
    // given
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setElementId("PI");
    processInstanceRecord.setBpmnProcessId(wrapString("process1"));
    processInstanceRecord.setProcessInstanceKey(1000L);
    processInstanceRecord.setVersion(1);
    processInstanceRecord.setProcessDefinitionKey(2);
    processInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.intent(ProcessInstanceIntent.CANCEL);
    metadata.valueType(ValueType.PROCESS_INSTANCE);

    final TypedRecordImpl typedEvent = new TypedRecordImpl(1);
    final LoggedEvent loggedEvent = mock(LoggedEvent.class);
    when(loggedEvent.getPosition()).thenReturn(1024L);

    typedEvent.wrap(loggedEvent, metadata, processInstanceRecord);
    final var consumer = mock(Consumer.class);

    // when - no banning of process instance
    bannedInstanceState.tryToBanInstance(typedEvent, consumer);

    // then
    assertThat(bannedInstanceState.isBanned(typedEvent)).isFalse();
    verify(consumer, never()).accept(1000L);
  }

  @Test
  public void shouldReturnFalseIfDifferentInstanceIsBanned() {
    // given
    final var processInstanceRecord = createRecord();
    final var differentProcessInstanceRecord = createRecord(1001);

    // when
    bannedInstanceState.tryToBanInstance(processInstanceRecord, (pi) -> {});

    // then
    assertThat(bannedInstanceState.isBanned(processInstanceRecord)).isTrue();
    assertThat(bannedInstanceState.isBanned(differentProcessInstanceRecord)).isFalse();
  }

  private TypedRecordImpl createRecord() {
    return createRecord(1000L);
  }

  private TypedRecordImpl createRecord(final long processInstanceKey) {
    final ProcessInstanceRecord processInstanceRecord = new ProcessInstanceRecord();
    processInstanceRecord.setElementId("startEvent");
    processInstanceRecord.setBpmnProcessId(wrapString("process1"));
    processInstanceRecord.setProcessInstanceKey(processInstanceKey);
    processInstanceRecord.setFlowScopeKey(1001L);
    processInstanceRecord.setVersion(1);
    processInstanceRecord.setProcessDefinitionKey(2);
    processInstanceRecord.setBpmnElementType(BpmnElementType.START_EVENT);

    final RecordMetadata metadata = new RecordMetadata();
    metadata.intent(ProcessInstanceIntent.ACTIVATE_ELEMENT);
    metadata.valueType(ValueType.PROCESS_INSTANCE);

    final TypedRecordImpl typedEvent = new TypedRecordImpl(1);
    final LoggedEvent loggedEvent = mock(LoggedEvent.class);
    when(loggedEvent.getPosition()).thenReturn(1024L);

    typedEvent.wrap(loggedEvent, metadata, processInstanceRecord);

    return typedEvent;
  }
}
