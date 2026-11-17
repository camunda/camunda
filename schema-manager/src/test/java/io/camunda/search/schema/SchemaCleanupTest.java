/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SchemaCleanupTest {
  private SearchEngineClient mockSearchEngineClient;

  @BeforeEach
  void setup() {
    mockSearchEngineClient = mock(SearchEngineClient.class);
    when(mockSearchEngineClient.getEngineName()).thenReturn("elasticsearch");
  }

  @Test
  void shouldNotMakeChangesWhenPerformCleanupIsFalse() {
    final SchemaCleanup schemaCleanup = new SchemaCleanup(false, mockSearchEngineClient);

    /* stub getIndexNames(String) for test purposes */
    when(mockSearchEngineClient.getIndexNames(anyString()))
        .thenAnswer(
            invocation -> {
              final String pattern = invocation.getArgument(0);
              return Set.of(pattern.split(","));
            });

    schemaCleanup.performCleanup();

    /* the path to delete the indexes is not followed */
    verify(mockSearchEngineClient, never()).deleteIndexIfExists(any());
  }

  @Test
  void shouldMakeChangesWhenPerformCleanupIsTrue() {
    final SchemaCleanup schemaCleanup = new SchemaCleanup(true, mockSearchEngineClient);

    /* stub getIndexNames(String) for test purposes */
    when(mockSearchEngineClient.getIndexNames(anyString()))
        .thenAnswer(
            invocation -> {
              final String pattern = invocation.getArgument(0);
              return Set.of(pattern.split(","));
            });

    schemaCleanup.performCleanup();

    /* the path to delete the indexes is followed */
    for (final String indexToDelete : SchemaCleanup.CLEANUP_INDEXES) {
      verify(mockSearchEngineClient).deleteIndexIfExists(indexToDelete);
    }
  }
}
