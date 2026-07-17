/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class ZeebeProcessInstanceSubEntityImportServiceTest {

  @Test
  void shouldRejectSkeletonProcessInstancesWithoutBpmnProcessId() {
    // given
    final TestProcessInstanceSubEntityImportService underTest = createService();

    // when / then
    assertThatThrownBy(() -> underTest.createSkeletonProcessInstance(null, 100L, 200L, "tenant-a"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("process instance [100]")
        .hasMessageContaining("process definition [200]")
        .hasMessageContaining("bpmnProcessId");
  }

  @Test
  void shouldRejectSkeletonProcessInstancesWithBlankBpmnProcessId() {
    // given
    final TestProcessInstanceSubEntityImportService underTest = createService();

    // when / then
    assertThatThrownBy(() -> underTest.createSkeletonProcessInstance(" ", 100L, 200L, "tenant-a"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bpmnProcessId");
  }

  private TestProcessInstanceSubEntityImportService createService() {
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, Answers.RETURNS_DEEP_STUBS);
    when(configurationService.getJobExecutorQueueSize()).thenReturn(10);
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    return new TestProcessInstanceSubEntityImportService(
        configurationService,
        mock(ProcessInstanceWriter.class),
        1,
        mock(ProcessDefinitionReader.class),
        mock(DatabaseClient.class));
  }

  private static final class TestProcessInstanceSubEntityImportService
      extends ZeebeProcessInstanceSubEntityImportService<Object> {

    private TestProcessInstanceSubEntityImportService(
        final ConfigurationService configurationService,
        final ProcessInstanceWriter processInstanceWriter,
        final int partitionId,
        final ProcessDefinitionReader processDefinitionReader,
        final DatabaseClient databaseClient) {
      super(
          configurationService,
          processInstanceWriter,
          partitionId,
          processDefinitionReader,
          databaseClient,
          "test-source-index");
    }

    @Override
    List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
        final List<Object> records) {
      return List.of();
    }
  }
}
