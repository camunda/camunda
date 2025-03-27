/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessInstanceDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  @Mock private ProcessInstanceWriter mockProcessInstanceWriter;

  @Mock private ListViewTemplate mockProcessInstanceIndex;

  @Mock private OperateDateTimeFormatter mockDateTimeFormatter;

  private OpensearchProcessInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new OpensearchProcessInstanceDao(
            mockQueryWrapper,
            mockRequestWrapper,
            mockOpensearchClient,
            mockProcessInstanceIndex,
            mockProcessInstanceWriter,
            mockDateTimeFormatter);
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
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(ProcessInstance.class);
  }

  @Test
  public void testGetIndexName() {
    when(mockProcessInstanceIndex.getAlias()).thenReturn("processInstanceIndex");
    assertThat(underTest.getIndexName()).isEqualTo("processInstanceIndex");
    verify(mockProcessInstanceIndex, times(1)).getAlias();
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L))
        .isEqualTo("Error in reading process instance for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L))
        .isEqualTo("No process instances found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L))
        .isEqualTo("Found more than one process instances for key 1");
  }

  @Test
  public void testSearchByKey() {
    final SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    final org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName()))
        .thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    final OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    final List<ProcessInstance> validResults = Collections.singletonList(new ProcessInstance());
    when(mockDoc.searchValues(mockRequestBuilder, ProcessInstance.class)).thenReturn(validResults);

    final List<ProcessInstance> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).isSameAs(validResults);
    verify(mockQueryWrapper, times(1)).term(underTest.getKeyFieldName(), 1L);
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder(underTest.getIndexName());
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithAllNullFilterFields() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final Query<ProcessInstance> inputQuery =
        new Query<ProcessInstance>().setFilter(new ProcessInstance());

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final ProcessInstance filter =
        new ProcessInstance()
            .setKey(1L)
            .setProcessDefinitionKey(2L)
            .setParentKey(3L)
            .setParentFlowNodeInstanceKey(4L)
            .setProcessVersion(1)
            .setProcessVersionTag("tag-v1")
            .setBpmnProcessId("bpmnId")
            .setState("state")
            .setIncident(false)
            .setTenantId("tenant")
            .setStartDate("2024-01-19T18:39:05.196-0500")
            .setEndDate("2024-01-19T18:39:06.196-0500");

    final String expectedDateFormat = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT;
    when(mockDateTimeFormatter.getApiDateTimeFormatString()).thenReturn(expectedDateFormat);

    final Query<ProcessInstance> inputQuery = new Query<ProcessInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the process instance filter was added as a query term to the
    // query
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.PARENT_KEY, filter.getParentKey());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.PARENT_FLOW_NODE_INSTANCE_KEY, filter.getParentFlowNodeInstanceKey());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.VERSION, filter.getProcessVersion());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.VERSION_TAG, filter.getProcessVersionTag());
    verify(mockQueryWrapper, times(1))
        .term(ProcessInstance.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.INCIDENT, filter.getIncident());
    verify(mockQueryWrapper, times(1)).term(ProcessInstance.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1))
        .matchDateQuery(ProcessInstance.START_DATE, filter.getStartDate(), expectedDateFormat);
    verify(mockQueryWrapper, times(1))
        .matchDateQuery(ProcessInstance.END_DATE, filter.getEndDate(), expectedDateFormat);

    // Verify that the join relation was still set
    verify(mockQueryWrapper, times(1))
        .term(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);
  }
}
