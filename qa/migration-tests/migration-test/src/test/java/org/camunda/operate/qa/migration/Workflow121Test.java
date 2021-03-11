/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.qa.migration.util.AbstractMigrationTest;
import org.camunda.operate.qa.migration.v121.Workflow121DataGenerator;
import org.camunda.operate.schema.templates.IncidentTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.CollectionUtil.chooseOne;
import static org.camunda.operate.util.ThreadUtil.sleepFor;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class Workflow121Test extends AbstractMigrationTest {


  private String bpmnProcessId = Workflow121DataGenerator.WORKFLOW_BPMN_PROCESS_ID;
  private Set<String> workflowInstanceIds;

  @Before
  public void findWorkflowInstanceIds() {
    assumeThatWorkflowIsUnderTest(bpmnProcessId);
    if (workflowInstanceIds == null) {
      sleepFor(5_000);
      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Workflow instances list
      searchRequest.source()
          .query(joinWithAnd(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION), termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
      try {
        workflowInstanceIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(workflowInstanceIds).hasSize(Workflow121DataGenerator.WORKFLOW_INSTANCE_COUNT);
    }
  }

  @Test
  public void testIncidents() {
    SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias());
    searchRequest.source().query(termsQuery(IncidentTemplate.WORKFLOW_INSTANCE_KEY, workflowInstanceIds));
    List<IncidentEntity> incidents = entityReader.searchEntitiesFor(searchRequest, IncidentEntity.class);
    assertThat(incidents.size()).isEqualTo(Workflow121DataGenerator.INCIDENT_COUNT);
    assertThat(incidents.stream().allMatch(i -> i.getState() != null)).describedAs("Each incident has a state").isTrue();
    assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null)).describedAs("Each incident has an errorType").isTrue();
    IncidentEntity randomIncident = chooseOne(incidents);
    assertThat(randomIncident.getErrorMessageHash()).isNotNull();
  }

}
