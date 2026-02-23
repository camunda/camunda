/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.client.opensearch._types.query_dsl.Query.of;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessDefinitionDaoTest {

  private OpensearchProcessDefinitionDao processDefinitionDao;

  private OpensearchQueryDSLWrapper wrapper;

  @BeforeEach
  public void setup() {
    wrapper = spy(new OpensearchQueryDSLWrapper());
    processDefinitionDao =
        new OpensearchProcessDefinitionDao(
            wrapper,
            mock(OpensearchRequestDSLWrapper.class),
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));
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

    // when
    processDefinitionDao.buildFiltering(query);

    // then
    verify(wrapper, times(1)).term(ProcessDefinition.NAME, processDefinition.getName());
    verify(wrapper, times(1))
        .term(ProcessDefinition.BPMN_PROCESS_ID, processDefinition.getBpmnProcessId());
    verify(wrapper, times(1)).term(ProcessDefinition.TENANT_ID, processDefinition.getTenantId());
    verify(wrapper, times(1)).term(ProcessDefinition.VERSION, processDefinition.getVersion());
    verify(wrapper, times(1))
        .term(ProcessDefinition.VERSION_TAG, processDefinition.getVersionTag());
    verify(wrapper, times(1)).term(ProcessDefinition.KEY, processDefinition.getKey());
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearch() {
    // Given
    final Query<ProcessDefinition> query = new Query<>();
    final var filtering = of(q -> q.matchAll(new MatchAllQuery.Builder().build()));
    final var sortOptions = new ArrayList<SortOptions>();

    // Create a real instance instead of mock to test the actual behavior
    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify that source filter excludes bpmnXml
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithFilter() {
    // given - query with filters applied
    final ProcessDefinition filter =
        new ProcessDefinition()
            .setName("testProcess")
            .setBpmnProcessId("process-123")
            .setTenantId("<default>");
    final Query<ProcessDefinition> query = new Query<ProcessDefinition>().setFilter(filter);

    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify that bpmnXml is still excluded even with filters
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithPagination() {
    // given - query with pagination parameters set
    // Note: buildSearchRequest() doesn't apply pagination - that's done by buildPaging()
    // We're testing that BPMN XML exclusion works regardless of pagination settings
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>().setSize(50).setSearchAfter(new Object[] {100L});

    final var filtering = of(q -> q.matchAll(new MatchAllQuery.Builder().build()));
    final var sortOptions = new ArrayList<SortOptions>();

    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify BPMN XML exclusion is applied (pagination is applied separately)
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithSorting() {
    // given - query with custom sorting
    final Query<ProcessDefinition> query =
        new Query<ProcessDefinition>()
            .setSort(Query.Sort.listOf(ProcessDefinition.NAME, Query.Sort.Order.ASC));

    final var filtering = of(q -> q.matchAll(new MatchAllQuery.Builder().build()));
    final var sortOptions = new ArrayList<SortOptions>();

    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify exclusion works with sorting
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void shouldOnlyExcludeBpmnXmlFieldAndNotOthers() {
    // given
    final Query<ProcessDefinition> query = new Query<>();
    final var filtering = of(q -> q.matchAll(new MatchAllQuery.Builder().build()));
    final var sortOptions = new ArrayList<SortOptions>();

    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify ONLY bpmnXml is in the excludes list
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    final var excludes = builtRequest.source().filter().excludes();
    assertThat(excludes).hasSize(1);
    assertThat(excludes).containsExactly(ProcessIndex.BPMN_XML);
    // Verify no includes are set (we want all other fields)
    assertThat(builtRequest.source().filter().includes()).isNullOrEmpty();
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearchWithComplexFilter() {
    // given - query with multiple filters (corner case: all fields set)
    final ProcessDefinition filter =
        new ProcessDefinition()
            .setKey(123L)
            .setName("complexProcess")
            .setBpmnProcessId("process-complex-123")
            .setTenantId("tenant-42")
            .setVersion(5)
            .setVersionTag("v1.2.3");
    final Query<ProcessDefinition> query = new Query<ProcessDefinition>().setFilter(filter);

    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    final var realWrapper = spy(new OpensearchQueryDSLWrapper());
    final var realRequestWrapper = spy(new OpensearchRequestDSLWrapper());
    final var realProcessDefinitionDao =
        new OpensearchProcessDefinitionDao(
            realWrapper,
            realRequestWrapper,
            mock(RichOpenSearchClient.class),
            new ProcessIndex("test-index", false));

    // when
    final var searchRequest =
        realProcessDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify that bpmnXml is excluded even with complex filters
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }
}
