/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.entities.listview.ProcessInstanceState.COMPLETED;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.util.EntityReader;
import io.camunda.operate.qa.migration.v100.BigProcessDataGenerator;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ThreadUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BigProcessTest extends AbstractMigrationTest {

  private String bpmnProcessId = BigProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;

  @Autowired
  private EntityReader entityReader;

  @Test
  public void testBigInstanceIsCompleted() {
    assumeThatProcessIsUnderTest(bpmnProcessId);

    ThreadUtil.sleepFor(10_000);
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    searchRequest.source()
        .query(joinWithAnd(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
    final List<ProcessInstanceForListViewEntity> wfis = entityReader
        .searchEntitiesFor(searchRequest, ProcessInstanceForListViewEntity.class);

    assertThat(wfis).hasSize(1);
    assertThat(wfis.get(0).getState()).isEqualTo(COMPLETED);
  }

}
