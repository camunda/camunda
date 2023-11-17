/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessInstanceDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  @Mock
  private ProcessInstanceWriter mockProcessInstanceWriter;

  @Mock
  private ListViewTemplate mockProcessInstanceIndex;

  private OpensearchProcessInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchProcessInstanceDao(mockQueryWrapper, mockRequestWrapper, mockProcessInstanceIndex,
        mockOpensearchClient, mockProcessInstanceWriter);
  }

  @Test
  public void testGetUniqueSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(ListViewTemplate.KEY);
  }

  @Test
  public void testGetKeyFieldName() {
    assertThat(underTest.getKeyFieldName()).isEqualTo(ProcessInstance.KEY);
  }

  @Test
  public void testGetModelClass() {
    assertThat(underTest.getModelClass()).isEqualTo(ProcessInstance.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    assertThat(underTest.getIndexName()).isEqualTo("processInstanceIndex");
    verify(mockProcessInstanceIndex, times(1)).getAlias();
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L)).isEqualTo("Error in reading process instance for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L)).isEqualTo("No process instances found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L)).isEqualTo("Found more than one process instances for key 1");
  }

  @Test
  public void testSearchByKey() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName())).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    List<ProcessInstance> validResults = Collections.singletonList(new ProcessInstance());
    when(mockDoc.searchValues(mockRequestBuilder, ProcessInstance.class)).thenReturn(validResults);

    List<ProcessInstance> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).isSameAs(validResults);
    verify(mockQueryWrapper, times(1)).term(underTest.getKeyFieldName(), 1L);
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder(underTest.getIndexName());
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(new ProcessInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    ProcessInstance filter = new ProcessInstance().setKey(1L).setProcessDefinitionKey(2L).setParentKey(3L)
        .setParentFlowNodeInstanceKey(4L).setProcessVersion(1).setBpmnProcessId("bpmnId").setState("state")
        .setTenantId("tenant").setStartDate("01-01-2020").setEndDate("01-02-2020");

    Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the process instance filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_KEY, filter.getParentKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY, filter.getParentFlowNodeInstanceKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.VERSION, filter.getProcessVersion());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.START_DATE, filter.getStartDate());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.END_DATE, filter.getEndDate());

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1)).term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }
}
