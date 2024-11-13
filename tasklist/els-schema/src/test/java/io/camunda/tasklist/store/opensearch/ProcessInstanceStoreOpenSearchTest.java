/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.MultiTenancyProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceStoreOpenSearchTest {

  @Mock private ProcessInstanceIndex processInstanceIndex;
  @Mock private TaskStore taskStore;
  @Spy private List<ProcessInstanceDependant> processInstanceDependants = Collections.emptyList();
  @Spy private TaskVariableTemplate taskVariableTemplate = new TaskVariableTemplate();
  @Mock private RetryElasticsearchClient retryElasticsearchClient;
  @Mock private TenantAwareOpenSearchClient tenantAwareClient;
  @Mock private TasklistProperties tasklistProperties;

  @InjectMocks private ProcessInstanceStoreOpenSearch instance;

  @BeforeEach
  void setUp() {
    when(processInstanceIndex.getFullQualifiedName()).thenReturn("tasklist-process-instance-x.0.0");
  }

  @Test
  void deleteProcessInstanceWhenMultiTenancyEnabledAndProcessInstanceIsNotAccessible()
      throws IOException {
    // given
    final var processInstanceId = "2220123";
    when(tasklistProperties.getMultiTenancy())
        .thenReturn(new MultiTenancyProperties().setEnabled(true));
    final var searchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(), eq(ProcessInstanceEntity.class)))
        .thenReturn(searchResponse);
    final var hits = mock(HitsMetadata.class);
    when(searchResponse.hits()).thenReturn(hits);
    when(hits.hits()).thenReturn(Collections.emptyList());

    // when
    final var result = instance.deleteProcessInstance(processInstanceId);

    // then
    assertThat(result).isEqualTo(DeletionStatus.NOT_FOUND);
    verifyNoInteractions(
        retryElasticsearchClient, taskStore, taskVariableTemplate, processInstanceDependants);
  }
}
