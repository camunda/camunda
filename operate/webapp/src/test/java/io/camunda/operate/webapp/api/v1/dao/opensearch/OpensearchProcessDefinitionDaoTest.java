/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
class OpensearchProcessDefinitionDaoTest {

  @Mock private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock private RichOpenSearchClient mockOpensearchClient;

  @Mock private ProcessIndex mockProcessIndex;

  @InjectMocks private OpensearchProcessDefinitionDao underTest;

  @BeforeEach
  void setup() {
    underTest =
        new OpensearchProcessDefinitionDao(
            mockQueryWrapper, mockRequestWrapper, mockOpensearchClient, mockProcessIndex);
  }

  @Test
  void testBuildFilteringWithValidFields() {
    // Given
    final SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    final ProcessDefinition filter = new ProcessDefinition();
    filter
        .setKey(123L)
        .setBpmnProcessId("demoBpmnProcessId")
        .setTenantId("demoTenant")
        .setName("demoName")
        .setVersion(1)
        .setVersionTag("demoVersionTag");
    final Query<ProcessDefinition> inputQuery = new Query<ProcessDefinition>().setFilter(filter);

    // When
    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Then
    verify(mockQueryWrapper, times(1)).term(ProcessDefinition.NAME, filter.getName());
    verify(mockQueryWrapper, times(1))
        .term(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(mockQueryWrapper, times(1)).term(ProcessDefinition.TENANT_ID, filter.getTenantId());
    verify(mockQueryWrapper, times(1)).term(ProcessDefinition.VERSION, filter.getVersion());
    verify(mockQueryWrapper, times(1)).term(ProcessDefinition.VERSION_TAG, filter.getVersionTag());
  }
}
