/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Checks the order and keys of the written records, which the engine tests can't observe directly.
 * The outcomes are covered with real state in {@link ProcessDefinitionSuspensionTest}.
 */
class ProcessDefinitionSuspensionResumeProcessorTest {

  private static final String PROCESS_ID = "process";
  private static final String TENANT_ID = "<default>";
  private static final long FIRST_KEY = 100L;

  private final ProcessState processState = mock(ProcessState.class);
  private final StateWriter stateWriter = mock(StateWriter.class);
  private final TypedResponseWriter responseWriter = mock(TypedResponseWriter.class);
  private final CommandDistributionBehavior commandDistributionBehavior =
      mock(CommandDistributionBehavior.class, RETURNS_DEEP_STUBS);
  private ProcessingState processingState;
  private Writers writers;
  private CslAuthorizationCheck cslCheck;
  private CslTenantCheck tenantCheck;
  private KeyGenerator keyGenerator;

  @BeforeEach
  void setUp() {
    processingState = mock(ProcessingState.class);
    when(processingState.getProcessState()).thenReturn(processState);

    writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.response()).thenReturn(responseWriter);
    when(writers.rejection()).thenReturn(mock(TypedRejectionWriter.class));

    cslCheck = mock(CslAuthorizationCheck.class);
    when(cslCheck.checkAuthorizationAndTenant(any(), any(), any(), any(), anyString(), any()))
        .thenAnswer(invocation -> Either.right(invocation.getArgument(2)));
    when(cslCheck.checkAuthorizationAndTenant(
            any(), any(), any(), any(), any(), anyString(), any()))
        .thenAnswer(invocation -> Either.right(invocation.getArgument(2)));

    tenantCheck = mock(CslTenantCheck.class);
    when(tenantCheck.resolveAuthorizedTenants(any()))
        .thenReturn(TenantAccess.allowed(List.of(TENANT_ID)));

    // only suspended versions are resumed and get a Process event
    when(processState.isSuspended(anyLong())).thenReturn(true);

    keyGenerator = mock(KeyGenerator.class);
    final var nextKey = new AtomicLong(FIRST_KEY);
    when(keyGenerator.nextKey()).thenAnswer(invocation -> nextKey.getAndIncrement());
  }

  @Test
  void shouldGiveEachEventItsOwnKey() {
    // given
    deployedProcess(10L, 1);
    deployedProcess(20L, 2);
    when(processState.getKnownProcessVersions(PROCESS_ID, TENANT_ID)).thenReturn(List.of(1L, 2L));
    final var processor =
        new ProcessDefinitionSuspensionResumeProcessor(
            processingState,
            keyGenerator,
            writers,
            cslCheck,
            tenantCheck,
            commandDistributionBehavior);
    final var command =
        command(new ProcessDefinitionSuspensionRecord().setBpmnProcessId(PROCESS_ID));

    // when
    processor.processRecord(command);

    // then - the request event and the response share the first key, each Process event gets
    // its own
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(FIRST_KEY),
            eq(ProcessDefinitionSuspensionIntent.RESUMED),
            any(ProcessDefinitionSuspensionRecord.class));
    verify(responseWriter)
        .writeAcceptedResponseOnCommand(
            eq(FIRST_KEY), eq(ProcessDefinitionSuspensionIntent.RESUMED), any(), eq(command));
    final var keys = ArgumentCaptor.forClass(Long.class);
    final var records = ArgumentCaptor.forClass(ProcessRecord.class);
    verify(stateWriter, times(2))
        .appendFollowUpEvent(keys.capture(), eq(ProcessIntent.RESUMED), records.capture());
    assertThat(keys.getAllValues()).containsExactly(FIRST_KEY + 1, FIRST_KEY + 2);
    assertThat(records.getAllValues())
        .extracting(ProcessRecord::getProcessDefinitionKey)
        .containsExactly(10L, 20L);
  }

  @Test
  void shouldWriteProcessEventsBeforeRequestEvent() {
    // given
    deployedProcess(10L, 1);
    final var processor =
        new ProcessDefinitionSuspensionResumeProcessor(
            processingState,
            keyGenerator,
            writers,
            cslCheck,
            tenantCheck,
            commandDistributionBehavior);

    // when
    processor.processRecord(
        command(new ProcessDefinitionSuspensionRecord().setProcessDefinitionKey(10L)));

    // then - like resource deletion, the request event comes last
    final InOrder inOrder = inOrder(stateWriter);
    inOrder
        .verify(stateWriter)
        .appendFollowUpEvent(anyLong(), eq(ProcessIntent.RESUMED), any(ProcessRecord.class));
    inOrder
        .verify(stateWriter)
        .appendFollowUpEvent(
            anyLong(),
            eq(ProcessDefinitionSuspensionIntent.RESUMED),
            any(ProcessDefinitionSuspensionRecord.class));
  }

  @Test
  void shouldNotAcknowledgeDistributedCommandThatFailsUnexpectedly() {
    // given
    final var processor = newProcessor();
    final var command = distributedCommand();

    // when
    final var error = processor.tryHandleError(command, new IllegalStateException("expected"));

    // then - the distributing partition sends it again, instead of this partition silently
    // keeping the previous state
    assertThat(error).isEqualTo(ProcessingError.UNEXPECTED_ERROR);
    verify(commandDistributionBehavior, never()).acknowledgeCommand(any());
  }

  @Test
  void shouldAcknowledgeDistributedCommandThatExceedsTheBatchSize() {
    // given
    final var processor = newProcessor();
    final var command = distributedCommand();

    // when
    final var error =
        processor.tryHandleError(command, mock(ExceededBatchRecordSizeException.class));

    // then - it is rejected and would exceed the batch size on every attempt
    assertThat(error).isEqualTo(ProcessingError.EXPECTED_ERROR);
    verify(commandDistributionBehavior).acknowledgeCommand(command);
  }

  private ProcessDefinitionSuspensionResumeProcessor newProcessor() {
    return new ProcessDefinitionSuspensionResumeProcessor(
        processingState, keyGenerator, writers, cslCheck, tenantCheck, commandDistributionBehavior);
  }

  @SuppressWarnings("unchecked")
  private static TypedRecord<ProcessDefinitionSuspensionRecord> distributedCommand() {
    final TypedRecord<ProcessDefinitionSuspensionRecord> command = mock(TypedRecord.class);
    when(command.isCommandDistributed()).thenReturn(true);
    when(command.getValue())
        .thenReturn(new ProcessDefinitionSuspensionRecord().setBpmnProcessId(PROCESS_ID));
    return command;
  }

  private DeployedProcess deployedProcess(final long key, final int version) {
    final var process = mock(DeployedProcess.class);
    when(process.getKey()).thenReturn(key);
    when(process.getVersion()).thenReturn(version);
    when(process.getBpmnProcessId()).thenReturn(BufferUtil.wrapString(PROCESS_ID));
    when(process.getTenantId()).thenReturn(TENANT_ID);
    when(process.toProcessRecord())
        .thenAnswer(
            invocation ->
                new ProcessRecord()
                    .setKey(key)
                    .setVersion(version)
                    .setBpmnProcessId(PROCESS_ID)
                    .setTenantId(TENANT_ID));
    when(processState.getProcessByKeyAndTenant(key, TENANT_ID)).thenReturn(process);
    when(processState.getProcessByProcessIdAndVersion(any(), eq(version), eq(TENANT_ID)))
        .thenReturn(process);
    return process;
  }

  private static MockTypedRecord<ProcessDefinitionSuspensionRecord> command(
      final ProcessDefinitionSuspensionRecord value) {
    return new MockTypedRecord<>(-1L, new RecordMetadata(), value);
  }
}
