/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.server.DynamicToolsMcpStatelessServerFeatures.AsyncToolSpecification;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessInstanceServices;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DynamicProcessToolProviderTest {

  private static final CamundaAuthentication AUTHENTICATION =
      CamundaAuthentication.of(a -> a.user("testUser").tenant("tenant1"));

  @Mock private ProcessDefinitionServices processDefinitionServices;
  @Mock private ProcessInstanceServices processInstanceServices;
  @Mock private CamundaAuthenticationProvider authenticationProvider;
  @Mock private MultiTenancyConfiguration multiTenancyConfiguration;
  @Mock private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<ProcessDefinitionQuery> queryCaptor;

  private DynamicProcessToolProvider toolProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication()).thenReturn(AUTHENTICATION);
    doReturn(processDefinitionServices)
        .when(processDefinitionServices)
        .withAuthentication(any(CamundaAuthentication.class));

    toolProvider =
        new DynamicProcessToolProvider(
            processDefinitionServices,
            processInstanceServices,
            authenticationProvider,
            multiTenancyConfiguration,
            new JacksonMcpJsonMapper(objectMapper));
  }

  @Test
  void shouldGenerateToolsFromAccessibleProcessDefinitions() {
    // Given
    final ProcessDefinitionEntity process1 =
        new ProcessDefinitionEntity(
            1L,
            "Test Process 1",
            "test-process-1",
            "<bpmn/>",
            "test-process-1.bpmn",
            1,
            "v1",
            "tenant1",
            null);

    final ProcessDefinitionEntity process2 =
        new ProcessDefinitionEntity(
            2L,
            "Test Process 2",
            "test-process-2",
            "<bpmn/>",
            "test-process-2.bpmn",
            1,
            "v1",
            "tenant1",
            null);

    final SearchQueryResult<ProcessDefinitionEntity> searchResult =
        SearchQueryResult.of(b -> b.total(2L).items(List.of(process1, process2)));

    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
        .thenReturn(searchResult);

    // When
    final List<AsyncToolSpecification> toolSpecs = toolProvider.getToolSpecifications();

    // Then
    assertThat(toolSpecs).hasSize(2);

    // Verify process definition query was made
    verify(processDefinitionServices).search(queryCaptor.capture());
    final ProcessDefinitionQuery capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isNotNull();

    // Verify tool names are generated correctly
    assertThat(toolSpecs)
        .extracting(spec -> spec.tool().name())
        .containsExactlyInAnyOrder("start_test_process_1_v1", "start_test_process_2_v1");

    // Verify tool descriptions contain process information
    assertThat(toolSpecs)
        .extracting(spec -> spec.tool().description())
        .allSatisfy(
            description -> {
              assertThat(description).contains("Create a new instance of the process");
              assertThat(description).contains("Process ID:");
              assertThat(description).contains("Version:");
            });
  }

  @Test
  void shouldSanitizeProcessIdForToolName() {
    // Given - process ID with special characters
    final ProcessDefinitionEntity process =
        new ProcessDefinitionEntity(
            1L,
            "Complex Process",
            "test-process.with-special:chars",
            "<bpmn/>",
            "test.bpmn",
            2,
            "v2",
            "tenant1",
            null);

    final SearchQueryResult<ProcessDefinitionEntity> searchResult =
        SearchQueryResult.of(b -> b.total(1L).items(List.of(process)));

    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
        .thenReturn(searchResult);

    // When
    final List<AsyncToolSpecification> toolSpecs = toolProvider.getToolSpecifications();

    // Then
    assertThat(toolSpecs).hasSize(1);
    // Special characters should be replaced with underscores
    assertThat(toolSpecs.get(0).tool().name())
        .isEqualTo("start_test_process_with_special_chars_v2");
  }

  @Test
  void shouldReturnEmptyListWhenNoProcessDefinitionsFound() {
    // Given
    final SearchQueryResult<ProcessDefinitionEntity> emptyResult =
        SearchQueryResult.of(b -> b.total(0L).items(List.of()));

    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
        .thenReturn(emptyResult);

    // When
    final List<AsyncToolSpecification> toolSpecs = toolProvider.getToolSpecifications();

    // Then
    assertThat(toolSpecs).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenSearchFails() {
    // Given
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
        .thenThrow(new RuntimeException("Search failed"));

    // When
    final List<AsyncToolSpecification> toolSpecs = toolProvider.getToolSpecifications();

    // Then - should handle gracefully and return empty list
    assertThat(toolSpecs).isEmpty();
  }

  @Test
  void shouldUseCorrectSearchQueryBuilder() {
    // Given
    final SearchQueryResult<ProcessDefinitionEntity> emptyResult =
        SearchQueryResult.of(b -> b.total(0L).items(List.of()));

    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
        .thenReturn(emptyResult);

    // When
    toolProvider.getToolSpecifications();

    // Then
    verify(processDefinitionServices).search(any(ProcessDefinitionQuery.class));
  }
}
