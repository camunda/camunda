/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractImportIT extends AbstractIT {

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  protected void allEntriesInElasticsearchHaveAllData(String elasticsearchIndex, final Set<String> excludedFields) {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, 1L, excludedFields);
  }


  protected void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                               final long count) {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, count, Collections.emptySet());
  }

  protected void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                               final long count,
                                                               final Set<String> nullValueFields) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(elasticsearchIndex);

    assertThat(idsResp.getHits().getTotalHits().value, is(count));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertAllFieldsSet(nullValueFields, searchHit);
    }
  }

  protected void assertAllFieldsSet(final Set<String> nullValueFields, final SearchHit searchHit) {
    for (Map.Entry<String, Object> searchHitField : searchHit.getSourceAsMap().entrySet()) {
      if (nullValueFields.contains(searchHitField.getKey())) {
        assertThat(searchHitField.getValue(), is(nullValue()));
      } else {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() +
          ". Should actually have a value!";
        assertThat(errorMessage, searchHitField.getValue(), is(notNullValue()));
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(errorMessage, value.isEmpty(), is(false));
        }
      }
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected BpmnModelInstance createSimpleProcessDefinition() {
    // @formatter:off
    return Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
  }

  protected ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    // @formatter:on
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }
}
