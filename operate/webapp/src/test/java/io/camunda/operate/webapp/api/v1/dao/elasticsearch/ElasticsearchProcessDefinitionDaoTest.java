/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchTestHelper.unwrapQueryVal;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchProcessDefinitionDaoTest {

  @Mock private ElasticsearchClient esClient;
  @Mock private ElasticsearchTenantHelper tenantHelper;
  @Mock private ProcessIndex processIndex;

  @InjectMocks @Spy private ElasticsearchProcessDefinitionDao processDefinitionDao;

  @BeforeEach
  public void setup() {
    // Configure tenantHelper to return query unchanged (no tenant filtering in tests)
    // Using lenient() because not all tests call search() method that uses these mocks
    lenient()
        .when(
            tenantHelper.makeQueryTenantAware(
                any(co.elastic.clients.elasticsearch._types.query_dsl.Query.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Configure processIndex to return an alias
    lenient().when(processIndex.getAlias()).thenReturn("process-index");
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
    final var searchReqBuilder = spy(new SearchRequest.Builder());
    // when
    processDefinitionDao.buildQueryOn(query, ProcessDefinition.KEY, searchReqBuilder, false);

    final ArgumentCaptor<co.elastic.clients.elasticsearch._types.query_dsl.Query> captor =
        ArgumentCaptor.forClass(co.elastic.clients.elasticsearch._types.query_dsl.Query.class);

    // verify interaction and capture argument
    Mockito.verify(searchReqBuilder).query(captor.capture());

    // get captured value
    final var queries = captor.getValue().bool().must();

    assertThat(queries.size()).isEqualTo(6);

    assertThat(queries.get(0).terms().field()).isEqualTo(ProcessDefinition.NAME);
    assertThat(unwrapQueryVal(queries.get(0), String.class)).isEqualTo(processDefinition.getName());

    assertThat(queries.get(1).terms().field()).isEqualTo(ProcessDefinition.BPMN_PROCESS_ID);
    assertThat(unwrapQueryVal(queries.get(1), String.class))
        .isEqualTo(processDefinition.getBpmnProcessId());

    assertThat(queries.get(2).terms().field()).isEqualTo(ProcessDefinition.TENANT_ID);
    assertThat(unwrapQueryVal(queries.get(2), String.class))
        .isEqualTo(processDefinition.getTenantId());

    assertThat(queries.get(3).terms().field()).isEqualTo(ProcessDefinition.VERSION);
    assertThat(unwrapQueryVal(queries.get(3), Integer.class))
        .isEqualTo(processDefinition.getVersion());

    assertThat(queries.get(4).terms().field()).isEqualTo(ProcessDefinition.VERSION_TAG);
    assertThat(unwrapQueryVal(queries.get(4), String.class))
        .isEqualTo(processDefinition.getVersionTag());

    assertThat(queries.get(5).terms().field()).isEqualTo(ProcessDefinition.KEY);
    assertThat(unwrapQueryVal(queries.get(5), Long.class)).isEqualTo(processDefinition.getKey());
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearch() throws APIException, IOException {
    // given
    final Query<ProcessDefinition> query = new Query<>();

    // Mock the searchWithResultsReturn method to avoid actual ES calls
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify that source exclusion is applied
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();
    assertThat(capturedRequest.source().filter().excludes()).contains(BPMN_XML);
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

    // Mock the searchWithResultsReturn method
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is still excluded with filters
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();
    assertThat(capturedRequest.source().filter().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithPagination() throws APIException, IOException {
    // given - query with pagination parameters
    // Note: We're testing that BPMN XML exclusion works regardless of pagination
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>().setSize(100).setSearchAfter(new Object[] {50L});

    // Mock the searchWithResultsReturn method
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is excluded
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();
    assertThat(capturedRequest.source().filter().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldOnlyExcludeBpmnXmlAndNotOtherFields() throws APIException, IOException {
    // given - ensure only BPMN XML is excluded, not other process definition fields
    final Query<ProcessDefinition> query = new Query<>();

    // Mock the searchWithResultsReturn method
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify ONLY bpmnXml is excluded
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();

    final var excludes = capturedRequest.source().filter().excludes();
    assertThat(excludes).hasSize(1);
    assertThat(excludes).containsExactly(BPMN_XML);

    // Verify no includes are set (we want all other fields)
    assertThat(capturedRequest.source().filter().includes()).isNullOrEmpty();
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

    // Mock the searchWithResultsReturn method
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is excluded even with complex query
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();
    assertThat(capturedRequest.source().filter().excludes()).contains(BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithSorting() throws APIException, IOException {
    // given - query with custom sorting
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>()
            .setSort(Query.Sort.listOf(ProcessDefinition.NAME, Query.Sort.Order.ASC));

    // Mock the searchWithResultsReturn method
    doReturn(new Results<ProcessDefinition>())
        .when(processDefinitionDao)
        .searchWithResultsReturn(any(), any());

    // when
    processDefinitionDao.search(query);

    // then - verify BPMN XML is excluded with sorting
    final ArgumentCaptor<SearchRequest> searchRequestCaptor =
        ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(processDefinitionDao)
        .searchWithResultsReturn(searchRequestCaptor.capture(), any());

    final SearchRequest capturedRequest = searchRequestCaptor.getValue();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().filter()).isNotNull();
    assertThat(capturedRequest.source().filter().excludes()).contains(BPMN_XML);
  }
}
