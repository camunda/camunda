/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.clients.reader.IncidentDocumentReader;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentErrorHashCodeNormalizerTest {

  private IncidentDocumentReader incidentReader;
  private IncidentErrorHashCodeNormalizer normalizer;
  private ResourceAccessChecks resourceAccessChecks;

  @BeforeEach
  void setUp() {
    incidentReader = mock(IncidentDocumentReader.class);
    normalizer = new IncidentErrorHashCodeNormalizer(incidentReader);
    resourceAccessChecks = mock(ResourceAccessChecks.class);
  }

  @Test
  void shouldResolveErrorHashCodeForProcessInstance() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("ResolvedError");

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result.errorMessageOperations()).hasSize(1);
    assertThat(result.errorMessageOperations().getFirst().value()).isEqualTo("ResolvedError");
    assertThat(result.incidentErrorHashCodeOperations()).isEmpty();
  }

  @Test
  void shouldReturnNullIfUnresolvedHashCodeForProcessInstance() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("");

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result).isNull();
  }

  @Test
  void shouldHandleOrFiltersForProcessInstance() {
    final var subFilter1 =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var subFilter2 =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessInstanceFilter.Builder().orFilters(List.of(subFilter1, subFilter2)).build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("Error1", "");

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result.orFilters()).hasSize(1);
    assertThat(result.orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Error1");
  }

  @Test
  void shouldResolveErrorHashCodeForProcessDefinition() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("ResolvedError");

    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result.errorMessageOperations()).hasSize(1);
    assertThat(result.errorMessageOperations().getFirst().value()).isEqualTo("ResolvedError");
    assertThat(result.incidentErrorHashCodeOperations()).isEmpty();
  }

  @Test
  void shouldReturnNullIfUnresolvedHashCodeForProcessDefinition() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("");

    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result).isNull();
  }

  @Test
  void shouldHandleOrFiltersForProcessDefinition() {
    final var subFilter1 =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var subFilter2 =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .orFilters(List.of(subFilter1, subFilter2))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(any(), any())).thenReturn("Error1", "");

    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result.orFilters()).hasSize(1);
    assertThat(result.orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Error1");
  }
}
