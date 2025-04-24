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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchProcessDefinitionDaoTest {

  @Spy
  ElasticsearchProcessDefinitionDao processDefinitionDao = new ElasticsearchProcessDefinitionDao();

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
}
