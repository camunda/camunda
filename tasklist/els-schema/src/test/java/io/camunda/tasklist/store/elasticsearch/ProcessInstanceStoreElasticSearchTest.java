/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.MultiTenancyProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessInstanceStoreElasticSearchTest {

  @Mock private ProcessInstanceIndex processInstanceIndex;
  @Mock private TaskStore taskStore;
  @Spy private List<ProcessInstanceDependant> processInstanceDependants = Collections.emptyList();
  @Spy private TaskVariableTemplate taskVariableTemplate = new TaskVariableTemplate();
  @Mock private RetryElasticsearchClient retryElasticsearchClient;
  @Mock private TenantAwareElasticsearchClient tenantAwareClient;
  @Mock private TasklistProperties tasklistProperties;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private ProcessInstanceStoreElasticSearch instance;

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
    when(tenantAwareClient.search(any())).thenReturn(searchResponse);
    final var searchHits = mock(SearchHits.class);
    when(searchResponse.getHits()).thenReturn(searchHits);
    when(searchHits.getTotalHits()).thenReturn(new TotalHits(0L, TotalHits.Relation.EQUAL_TO));

    // when
    final var result = instance.deleteProcessInstance(processInstanceId);

    // then
    assertThat(result).isEqualTo(DeletionStatus.NOT_FOUND);
    verifyNoInteractions(
        retryElasticsearchClient,
        objectMapper,
        taskStore,
        taskVariableTemplate,
        processInstanceDependants);
  }
}
