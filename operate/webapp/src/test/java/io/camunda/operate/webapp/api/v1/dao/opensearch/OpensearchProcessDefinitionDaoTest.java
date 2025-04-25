/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

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
            new ProcessIndex());
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
    processDefinitionDao.buildFiltering(query, mock(SearchRequest.Builder.class));

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
}
