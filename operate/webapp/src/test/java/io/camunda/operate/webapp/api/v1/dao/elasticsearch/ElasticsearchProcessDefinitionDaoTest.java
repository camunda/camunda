/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchProcessDefinitionDaoTest {

  @Mock private TenantAwareElasticsearchClient tenantAwareClient;

  @Mock private ObjectMapper objectMapper;

  @Mock private ProcessIndex processIndex;

  @Spy private ElasticsearchProcessDefinitionDao processDefinitionDao;

  @BeforeEach
  public void setup() {
    ReflectionTestUtils.setField(processDefinitionDao, "tenantAwareClient", tenantAwareClient);
    ReflectionTestUtils.setField(processDefinitionDao, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(processDefinitionDao, "processIndex", processIndex);
    when(processIndex.getAlias()).thenReturn("process-index");
  }

  @Test
  public void shouldApplyFilters() {
    // given
    final ProcessDefinition processDefinition =
        new ProcessDefinition()
            .setKey(1L)
            .setName("testProcess")
            .setBpmnProcessId("123")
            .setTenantId("<default>")
            .setVersion(1)
            .setVersionTag("testTag");
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>().setFilter(processDefinition);
    final SearchSourceBuilder searchSourceBuilder = spy(new SearchSourceBuilder());

    // when
    processDefinitionDao.buildQueryOn(query, ProcessDefinition.KEY, searchSourceBuilder);

    // then
    verify(processDefinitionDao, times(1)).buildFiltering(query, searchSourceBuilder);
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.NAME, processDefinition.getName());
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.BPMN_PROCESS_ID, processDefinition.getBpmnProcessId());
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.TENANT_ID, processDefinition.getTenantId());
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.VERSION, processDefinition.getVersion());
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.VERSION_TAG, processDefinition.getVersionTag());
    verify(processDefinitionDao, times(1))
        .buildTermQuery(ProcessDefinition.KEY, processDefinition.getKey());
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearch() throws APIException, IOException {
    // given
    final Query<ProcessDefinition> query = new Query<>();
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - capture the SearchRequest and verify BPMN_XML is excluded
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().fetchSource()).isNotNull();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithFilters() throws APIException, IOException {
    // given - query with filters applied
    final ProcessDefinition filter =
        new ProcessDefinition()
            .setName("testProcess")
            .setBpmnProcessId("process-123")
            .setTenantId("<default>")
            .setVersion(2);
    final Query<ProcessDefinition> query = new Query<ProcessDefinition>().setFilter(filter);
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN_XML is excluded even with filters
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithPagination() throws APIException, IOException {
    // given - query with pagination parameters
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>().setSize(100).setSearchAfter(new Object[] {50L});
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN_XML is excluded
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldOnlyExcludeBpmnXmlAndNotOtherFields() throws APIException, IOException {
    // given - ensure only BPMN XML is excluded, not other process definition fields
    final Query<ProcessDefinition> query = new Query<>();
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - verify ONLY bpmnXml is in the excludes list
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    final String[] excludes = capturedRequest.source().fetchSource().excludes();
    assertThat(excludes).hasSize(1);
    assertThat(excludes).containsExactly(BPMN_XML);

    // Verify no includes are set (we want all other fields)
    assertThat(capturedRequest.source().fetchSource().includes()).isNullOrEmpty();
  }

  @Test
  public void shouldExcludeBpmnXmlWithComplexQuery() throws APIException, IOException {
    // given - complex query with all filter fields populated
    final ProcessDefinition filter =
        new ProcessDefinition()
            .setKey(999L)
            .setName("complexProcess")
            .setBpmnProcessId("complex-process-id")
            .setTenantId("complex-tenant")
            .setVersion(10)
            .setVersionTag("v2.0.0");
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>()
            .setFilter(filter)
            .setSize(25)
            .setSort(Query.Sort.listOf(ProcessDefinition.VERSION, Query.Sort.Order.DESC));
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is excluded even with complex query
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithSorting() throws APIException, IOException {
    // given - query with custom sorting
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>()
            .setSort(Query.Sort.listOf(ProcessDefinition.NAME, Query.Sort.Order.ASC));
    mockSearchResponse();

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is excluded with sorting
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  private void mockSearchResponse() throws IOException {
    final SearchResponse mockResponse = org.mockito.Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = org.mockito.Mockito.mock(SearchHits.class);
    final org.apache.lucene.search.TotalHits.Relation mockRelation =
        org.mockito.Mockito.mock(org.apache.lucene.search.TotalHits.Relation.class);
    final org.apache.lucene.search.TotalHits mockTotalHits =
        new org.apache.lucene.search.TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(mockHits.getHits()).thenReturn(new SearchHit[0]);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(mockResponse);
  }
}
