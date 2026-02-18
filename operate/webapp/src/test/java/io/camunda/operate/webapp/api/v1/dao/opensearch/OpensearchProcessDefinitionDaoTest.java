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

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessDefinitionDaoTest {

  private OpensearchProcessDefinitionDao processDefinitionDao;
  private OpensearchQueryDSLWrapper queryWrapper;
  private OpensearchRequestDSLWrapper requestWrapper;

  @BeforeEach
  public void setup() {
    queryWrapper = spy(new OpensearchQueryDSLWrapper());
    requestWrapper = spy(new OpensearchRequestDSLWrapper());
    processDefinitionDao =
        new OpensearchProcessDefinitionDao(
            queryWrapper,
            requestWrapper,
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
    verify(queryWrapper, times(1)).term(ProcessDefinition.NAME, processDefinition.getName());
    verify(queryWrapper, times(1))
        .term(ProcessDefinition.BPMN_PROCESS_ID, processDefinition.getBpmnProcessId());
    verify(queryWrapper, times(1))
        .term(ProcessDefinition.TENANT_ID, processDefinition.getTenantId());
    verify(queryWrapper, times(1)).term(ProcessDefinition.VERSION, processDefinition.getVersion());
    verify(queryWrapper, times(1))
        .term(ProcessDefinition.VERSION_TAG, processDefinition.getVersionTag());
    verify(queryWrapper, times(1)).term(ProcessDefinition.KEY, processDefinition.getKey());
  }

  @Test
  public void shouldExcludeBpmnXmlFromSearch() {
    // given
    final Query<ProcessDefinition> query = new Query<>();
    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    // when
    final var searchRequest =
        processDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
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
            .setTenantId("<default>")
            .setVersion(2);
    final Query<ProcessDefinition> query = new Query<ProcessDefinition>().setFilter(filter);
    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    // when
    final var searchRequest =
        processDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify that bpmnXml is still excluded even with filters
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void shouldOnlyExcludeBpmnXmlFieldAndNotOthers() {
    // given - ensure only BPMN XML is excluded, not other process definition fields
    final Query<ProcessDefinition> query = new Query<>();
    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    // when
    final var searchRequest =
        processDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
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
  public void shouldExcludeBpmnXmlWithComplexFilter() {
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
    final var filtering = processDefinitionDao.buildFiltering(query);
    final var sortOptions = new ArrayList<SortOptions>();

    // when
    final var searchRequest =
        processDefinitionDao.buildSearchRequest(query, filtering, sortOptions);
    final var builtRequest = searchRequest.build();

    // then - verify that bpmnXml is excluded even with complex filters
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }
}
