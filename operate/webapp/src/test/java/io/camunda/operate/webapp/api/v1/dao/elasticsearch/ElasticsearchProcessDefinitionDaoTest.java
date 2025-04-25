/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElasticsearchProcessDefinitionDaoTest {

  @Mock private SearchSourceBuilder mockSearchSourceBuilder;

  private ElasticsearchProcessDefinitionDao underTest;

  @BeforeEach
  void setup() {
    underTest = spy(new ElasticsearchProcessDefinitionDao());
  }

  @Test
  void testBuildFilteringWithValidFields() {
    // Given
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
    underTest.buildFiltering(inputQuery, mockSearchSourceBuilder);

    // Then
    verify(underTest, times(1)).buildTermQuery(ProcessDefinition.NAME, filter.getName());
    verify(underTest, times(1))
        .buildTermQuery(ProcessDefinition.BPMN_PROCESS_ID, filter.getBpmnProcessId());
    verify(underTest, times(1)).buildTermQuery(ProcessDefinition.TENANT_ID, filter.getTenantId());
    verify(underTest, times(1)).buildTermQuery(ProcessDefinition.VERSION, filter.getVersion());
    verify(underTest, times(1))
        .buildTermQuery(ProcessDefinition.VERSION_TAG, filter.getVersionTag());
  }
}
