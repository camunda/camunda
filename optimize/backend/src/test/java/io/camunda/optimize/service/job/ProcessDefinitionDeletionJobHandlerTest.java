/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionDeletionJobHandlerTest {

  private static final String DEFINITION_ID = "definition-1";
  private static final String BPMN_PROCESS_ID = "invoice-process";

  private ProcessDefinitionReader processDefinitionReader;
  private ProcessInstanceWriter processInstanceWriter;
  private ProcessDefinitionWriter processDefinitionWriter;
  private ProcessDefinitionDeletionJobHandler handler;

  @BeforeEach
  void init() {
    processDefinitionReader = mock(ProcessDefinitionReader.class);
    processInstanceWriter = mock(ProcessInstanceWriter.class);
    processDefinitionWriter = mock(ProcessDefinitionWriter.class);
    handler =
        new ProcessDefinitionDeletionJobHandler(
            processDefinitionReader, processInstanceWriter, processDefinitionWriter);
  }

  @Test
  void shouldExposeJobTypeAndEntityType() {
    // when / then
    assertThat(handler.getJobType()).isEqualTo(JobType.DELETE);
    assertThat(handler.getEntityType()).isEqualTo(EntityType.PROCESS_DEFINITION);
  }

  @Test
  void shouldDeleteInstancesAndDefinitionWhenDefinitionExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter).deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldNoOpWhenDefinitionNoLongerExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID)).thenReturn(Optional.empty());

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter, never()).deleteInstancesByDefinitionId(anyString(), anyString());
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
  }

  @Test
  void shouldRetryOnRetryableErrorAndEventuallySucceed() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter, times(3))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldPropagateOnceRetryableErrorExceedsMaxAttempts() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when / then
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(OptimizeRuntimeException.class);
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
  }

  @Test
  void shouldNotRetryNonRetryableError() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID))
        .thenReturn(Optional.of(definition()));
    doThrow(new IllegalStateException("not retryable"))
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when / then
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(IllegalStateException.class);
    verify(processInstanceWriter, times(1))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
  }

  private JobRegistryEntryDto job() {
    return new JobRegistryEntryDto(JobType.DELETE, EntityType.PROCESS_DEFINITION, DEFINITION_ID);
  }

  private ProcessDefinitionOptimizeDto definition() {
    final ProcessDefinitionOptimizeDto definition = new ProcessDefinitionOptimizeDto();
    definition.setId(DEFINITION_ID);
    definition.setKey(BPMN_PROCESS_ID);
    return definition;
  }
}
