/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ILMPolicyUpdateOpenSearchTest {

  @Mock private RetryOpenSearchClient retryOpenSearchClient;
  @Mock private OpenSearchSchemaManager schemaManager;
  @Spy private TasklistProperties tasklistProperties;
  @InjectMocks private ILMPolicyUpdateOpenSearch instance;

  @Test
  void shouldCreatePolicyWhenManagePolicyEnabled() throws IOException {
    // given
    tasklistProperties.getArchiver().setIlmManagePolicy(true);
    when(retryOpenSearchClient.getIndexTemplateSettings(anyString())).thenReturn(null);
    when(retryOpenSearchClient.getIndexNames(anyString())).thenReturn(Set.of());

    // when
    instance.applyIlmPolicyToAllIndices();

    // then
    verify(schemaManager, times(1)).createIndexLifeCyclesIfNotExist();
  }

  @Test
  void shouldSkipPolicyCreationWhenManagePolicyDisabled() throws IOException {
    // given
    tasklistProperties.getArchiver().setIlmManagePolicy(false);
    when(retryOpenSearchClient.getIndexTemplateSettings(anyString())).thenReturn(null);
    when(retryOpenSearchClient.getIndexNames(anyString())).thenReturn(Set.of());

    // when
    instance.applyIlmPolicyToAllIndices();

    // then
    verify(schemaManager, never()).createIndexLifeCyclesIfNotExist();
  }
}
