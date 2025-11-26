/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OpensearchSequenceFlowDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private SequenceFlowTemplate mockSequenceFlowIndex;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  private OpensearchSequenceFlowDao underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchSequenceFlowDao(
            mockQueryWrapper, mockRequestWrapper, mockOpensearchClient, mockSequenceFlowIndex);
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(SequenceFlowTemplate.ID);
  }

  @Test
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(SequenceFlow.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockSequenceFlowIndex.getAlias()).thenReturn("sequenceFlowIndex");
    assertThat(underTest.getIndexName()).isEqualTo("sequenceFlowIndex");
    verify(mockSequenceFlowIndex, times(1)).getAlias();
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    final var filtering = underTest.buildFiltering(new Query<>());

    verify(mockQueryWrapper, times(1)).matchAll();
    assertThat(filtering).isEqualTo(mockQueryWrapper.matchAll());
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    final SequenceFlow filter =
        new SequenceFlow()
            .setId("id")
            .setActivityId("activity_id")
            .setTenantId("tenant")
            .setProcessInstanceKey(1L);

    final Query<SequenceFlow> inputQuery = new Query<SequenceFlow>().setFilter(filter);

    underTest.buildFiltering(inputQuery);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ID, filter.getId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ACTIVITY_ID, filter.getActivityId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1))
        .term(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
  }
}
