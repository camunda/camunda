/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.operate.util.ElasticsearchTestHelper.unwrapQueryVal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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
    final var searchReqBuilder = spy(new SearchRequest.Builder());
    // when
    processDefinitionDao.buildQueryOn(query, ProcessDefinition.KEY, searchReqBuilder, false);

    final ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);

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
}
