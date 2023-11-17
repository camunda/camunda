/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchSequenceFlowDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private SequenceFlowTemplate mockSequenceFlowIndex;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  private OpensearchSequenceFlowDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchSequenceFlowDao(mockQueryWrapper, mockRequestWrapper,
        mockSequenceFlowIndex, mockOpensearchClient);
  }

  @Test
  public void testGetSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(SequenceFlowTemplate.ID);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(SequenceFlow.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockSequenceFlowIndex.getAlias()).thenReturn("sequenceFlowIndex");
    assertThat(underTest.getIndexName()).isEqualTo("sequenceFlowIndex");
    verify(mockSequenceFlowIndex, times(1)).getAlias();
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Query<SequenceFlow> inputQuery = new Query<SequenceFlow>().setFilter(new SequenceFlow());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    SequenceFlow filter = new SequenceFlow().setId("id").setActivityId("activity_id").setTenantId("tenant")
        .setProcessInstanceKey(1L);

    Query<SequenceFlow> inputQuery = new Query<SequenceFlow>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ID, filter.getId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.ACTIVITY_ID, filter.getActivityId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1)).term(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
  }
}
