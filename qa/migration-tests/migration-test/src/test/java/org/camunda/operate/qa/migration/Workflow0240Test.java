/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.entities.listview.WorkflowInstanceState.COMPLETED;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.List;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.qa.migration.util.AbstractMigrationTest;
import org.camunda.operate.qa.migration.util.EntityReader;
import org.camunda.operate.qa.migration.v0240.Workflow0240DataGenerator;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ThreadUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class Workflow0240Test extends AbstractMigrationTest {

  private String bpmnProcessId = Workflow0240DataGenerator.WORKFLOW_BPMN_PROCESS_ID;

  @Autowired
  private EntityReader entityReader;

  @Test
  public void testBigInstanceIsCompleted() throws IOException {
    assumeThatWorkflowIsUnderTest(bpmnProcessId);

    ThreadUtil.sleepFor(10_000);
    SearchRequest searchRequest = new SearchRequest(
        entityReader.getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source()
        .query(joinWithAnd(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION),
            termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
    final List<WorkflowInstanceForListViewEntity> wfis = entityReader
        .searchEntitiesFor(searchRequest, WorkflowInstanceForListViewEntity.class);

    assertThat(wfis).hasSize(1);
    assertThat(wfis.get(0).getState()).isEqualTo(COMPLETED);
  }

}
