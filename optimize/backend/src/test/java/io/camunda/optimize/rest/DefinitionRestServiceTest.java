/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.collection.CollectionScopeService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefinitionRestServiceTest {

  private static final String USER_ID = "user-1";

  @Mock private DefinitionService definitionService;
  @Mock private CollectionScopeService collectionScopeService;
  @Mock private SessionService sessionService;
  @Mock private HttpServletRequest request;

  private DefinitionRestService underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new DefinitionRestService(definitionService, collectionScopeService, sessionService);
    when(sessionService.getRequestUserOrFailNotAuthorized(request)).thenReturn(USER_ID);
  }

  @Test
  void shouldFilterProcessDefinitionsWithAgentRunsWhenRequested() {
    // given
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID, false))
        .thenReturn(
            List.of(
                processDefinition("process-with-agent-runs"),
                processDefinition("process-without-agent-runs")));
    when(definitionService.getProcessDefinitionsWithAgentRuns(USER_ID))
        .thenReturn(Set.of("process-with-agent-runs"));

    // when
    final List<DefinitionOptimizeResponseDto> definitions =
        underTest.getDefinitions(DefinitionType.PROCESS, false, true, request);

    // then
    assertThat(definitions)
        .extracting(DefinitionOptimizeResponseDto::getKey)
        .containsExactly("process-with-agent-runs");
  }

  @Test
  void shouldNotFilterProcessDefinitionsWhenHasAgentRunsIsAbsent() {
    // given
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID, false))
        .thenReturn(
            List.of(
                processDefinition("process-with-agent-runs"),
                processDefinition("process-without-agent-runs")));

    // when
    final List<DefinitionOptimizeResponseDto> definitions =
        underTest.getDefinitions(DefinitionType.PROCESS, false, null, request);

    // then
    assertThat(definitions)
        .extracting(DefinitionOptimizeResponseDto::getKey)
        .containsExactly("process-with-agent-runs", "process-without-agent-runs");
    verify(definitionService, never()).getProcessDefinitionsWithAgentRuns(USER_ID);
  }

  @Test
  void shouldFilterProcessDefinitionKeysWithAgentRunsWhenRequested() {
    // given
    when(definitionService.getFullyImportedDefinitions(DefinitionType.PROCESS, USER_ID))
        .thenReturn(
            List.of(
                definitionResponse("process-with-agent-runs", "Process with agent runs"),
                definitionResponse("process-without-agent-runs", "Process without agent runs")));
    when(definitionService.getProcessDefinitionsWithAgentRuns(USER_ID))
        .thenReturn(Set.of("process-with-agent-runs"));

    // when
    final List<DefinitionKeyResponseDto> keys =
        underTest.getDefinitionKeys(DefinitionType.PROCESS, null, true, request);

    // then
    assertThat(keys)
        .extracting(DefinitionKeyResponseDto::getKey)
        .containsExactly("process-with-agent-runs");
  }

  @Test
  void shouldIgnoreHasAgentRunsForNonProcessDefinitions() {
    // given
    when(definitionService.getFullyImportedDefinitions(DefinitionType.DECISION, USER_ID, false))
        .thenReturn(List.of(decisionDefinition("decision-1")));

    // when
    final List<DefinitionOptimizeResponseDto> definitions =
        underTest.getDefinitions(DefinitionType.DECISION, false, true, request);

    // then
    assertThat(definitions)
        .extracting(DefinitionOptimizeResponseDto::getKey)
        .containsExactly("decision-1");
    verify(definitionService, never()).getProcessDefinitionsWithAgentRuns(USER_ID);
  }

  private static ProcessDefinitionOptimizeDto processDefinition(final String key) {
    final ProcessDefinitionOptimizeDto definition = new ProcessDefinitionOptimizeDto();
    definition.setKey(key);
    definition.setName(key);
    return definition;
  }

  private static DefinitionOptimizeResponseDto decisionDefinition(final String key) {
    final DecisionDefinitionOptimizeDto definition = new DecisionDefinitionOptimizeDto();
    definition.setKey(key);
    definition.setName(key);
    return definition;
  }

  private static DefinitionResponseDto definitionResponse(final String key, final String name) {
    return new DefinitionResponseDto(
        key,
        name,
        DefinitionType.PROCESS,
        List.of(new TenantDto("tenant-1", "Tenant 1", "zeebe")),
        Set.of("zeebe"));
  }
}
