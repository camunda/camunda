/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.migration.util.TestConstants.DEFAULT_TENANT_ID;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.util.EntityReader;
import io.camunda.operate.qa.migration.v110.BigProcessDataGenerator;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BigProcessTest extends AbstractMigrationTest {

  private final String bpmnProcessId = BigProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;

  @Autowired private EntityReader entityReader;

  @Test
  public void testBigInstanceIsCompleted() {
    assumeThatProcessIsUnderTest(bpmnProcessId);

    ThreadUtil.sleepFor(10_000);
    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
    final List<ProcessInstanceForListViewEntity> wfis =
        entityReader.searchEntitiesFor(searchRequest, ProcessInstanceForListViewEntity.class);

    assertThat(wfis).hasSize(1);
    assertThat(wfis.get(0).getState()).isEqualTo(COMPLETED);
  }

  @Test
  public void testProcess() {
    final SearchRequest searchRequest = new SearchRequest(processTemplate.getAlias());
    searchRequest.source().query(termQuery(EventTemplate.BPMN_PROCESS_ID, bpmnProcessId));
    final List<ProcessEntity> processes =
        entityReader.searchEntitiesFor(searchRequest, ProcessEntity.class);
    assertThat(processes).hasSize(1);
    assertThat(processes.get(0).getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
  }
}
