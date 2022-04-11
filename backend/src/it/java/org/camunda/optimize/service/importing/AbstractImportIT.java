/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.mockserver.model.HttpResponse;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public abstract class AbstractImportIT extends AbstractIT {

  protected void assertAllEntriesInElasticsearchHaveAllData(String elasticsearchIndex,
                                                            final Set<String> excludedFields) {
    assertAllEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, 1L, excludedFields);
  }


  protected void assertAllEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                                     final long count) {
    assertAllEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, count, Collections.emptySet());
  }

  protected void assertAllEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                                     final long count,
                                                                     final Set<String> nullValueFields) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(elasticsearchIndex);

    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(count);
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertAllFieldsSet(nullValueFields, searchHit);
    }
  }

  protected void assertAllFieldsSet(final Set<String> nullValueFields, final SearchHit searchHit) {
    for (Map.Entry<String, Object> searchHitField : searchHit.getSourceAsMap().entrySet()) {
      if (nullValueFields.contains(searchHitField.getKey())) {
        assertThat(searchHitField.getValue()).isNull();
      } else {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() +
          ". Should actually have a value!";
        assertThat(searchHitField.getValue()).withFailMessage(errorMessage).isNotNull();
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(value).withFailMessage(errorMessage).isNotEmpty();
        }
      }
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceProcessTaskWithVariables(variables);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceProcessTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = getSingleServiceTaskProcess();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      BpmnModels.getSingleUserTaskDiagram(),
      variables
    );
  }

  protected static Stream<ErrorResponseMock> engineErrors() {
    return MockServerUtil.engineMockedErrorResponses();
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<ErrorResponseMock> engineAuthorizationErrors() {
    return Stream.of(
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())),
      (request, times, mockServer) -> mockServer.when(request, times)
        .respond(HttpResponse.response().withStatusCode(Response.Status.FORBIDDEN.getStatusCode()))
    );
  }

  protected boolean indicesExist(final List<IndexMappingCreator> mappings) {
    return embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .indicesExist(embeddedOptimizeExtension.getOptimizeElasticClient(), mappings);
  }

}
