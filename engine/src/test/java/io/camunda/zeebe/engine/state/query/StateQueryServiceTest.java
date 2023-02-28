/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.QueryService.ClosedServiceException;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ProcessingStateExtension.class)
final class StateQueryServiceTest {

  private StateQueryService sut;
  private ZeebeDb<ZbColumnFamilies> db;
  private MutableProcessingState state;
  private TransactionContext transactionContext;

  @BeforeEach
  void setup() {
    sut = new StateQueryService(db);
  }

  @ParameterizedTest(name = "[{index}] should throw ClosedServiceException when closed")
  @MethodSource("provideOperations")
  void shouldFailWhenServiceIsClosed(final Consumer<StateQueryService> operation) {
    // given
    sut.close();

    // when - then
    assertThatCode(() -> operation.accept(sut)).isInstanceOf(ClosedServiceException.class);
  }

  private static Stream<Consumer<StateQueryService>> provideOperations() {
    return Stream.of(
        svc -> svc.getBpmnProcessIdForJob(1),
        svc -> svc.getBpmnProcessIdForProcess(1),
        svc -> svc.getBpmnProcessIdForProcessInstance(1));
  }

  @Nested
  @DisplayName("getBpmnProcessIdForProcess(processKey)")
  final class GetBpmnProcessIdForProcess {

    @Test
    @DisplayName("should return an empty optional when process is not found")
    void shouldReturnEmptyWhenNotFound() {
      // when
      final var key = Protocol.encodePartitionId(1, 1L);
      final var result = sut.getBpmnProcessIdForProcess(key);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return a bpmn process id when process is found")
    void shouldReturnMatchWhenPresent() {
      // given
      final var processId = "processId";
      final var key = Protocol.encodePartitionId(1, 1L);
      final var record = Records.process(key, processId);
      state.getProcessState().putProcess(record.getKey(), record);

      // when
      final var result = sut.getBpmnProcessIdForProcess(record.getKey());

      // then
      assertThat(result).contains(BufferUtil.wrapString(processId));
    }
  }

  @Nested
  @DisplayName("getBpmnProcessIdForProcessInstance(processInstanceKey)")
  final class GetBpmnProcessIdForProcessInstance {

    @Test
    @DisplayName("should return an empty optional when process instance is not found")
    void shouldReturnEmptyWhenNotFound() {
      // when
      final var key = Protocol.encodePartitionId(1, 1L);
      final var result = sut.getBpmnProcessIdForProcessInstance(key);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return a bpmn process id when process instance is found")
    void shouldReturnMatchWhenPresent() {
      // given
      final var processId = "processId";
      final var key = Protocol.encodePartitionId(1, 1L);
      final var record = Records.processInstance(key, processId);
      state
          .getElementInstanceState()
          .newInstance(key, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);

      // when
      final var result = sut.getBpmnProcessIdForProcessInstance(key);

      // then
      assertThat(result).contains(BufferUtil.wrapString(processId));
    }
  }

  @Nested
  @DisplayName("getBpmnProcessIdForJob(jobKey)")
  class GetBpmnProcessIdForJob {

    @Test
    @DisplayName("should return an empty optional when job is not found")
    void shouldReturnEmptyWhenNotFound() {
      // when
      final var key = Protocol.encodePartitionId(1, 1L);
      final var result = sut.getBpmnProcessIdForJob(key);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should return a bpmn process id when job is found")
    void shouldReturnMatchWhenPresent() {
      // given
      final var processId = "processId";
      final var key = Protocol.encodePartitionId(1, 1L);
      final var record = Records.job(key, processId);
      state.getJobState().create(key, record);

      // when
      final var result = sut.getBpmnProcessIdForJob(key);

      // then
      assertThat(result).contains(BufferUtil.wrapString(processId));
    }
  }
}
