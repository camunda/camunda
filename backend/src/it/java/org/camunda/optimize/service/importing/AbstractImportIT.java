/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import jakarta.ws.rs.core.Response;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Tag;
import org.mockserver.model.HttpResponse;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

@Tag("import")
public abstract class AbstractImportIT extends AbstractPlatformIT {

  protected <T> void assertAllEntriesInElasticsearchHaveAllData(String elasticsearchIndex,
                                                                final Class<T> type,
                                                                final Set<String> excludedFields) {
    assertAllEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, type, 1, excludedFields);
  }


  protected <T> void assertAllEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                                         final Class<T> type,
                                                                         final int count) {
    assertAllEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, type, count, Collections.emptySet());
  }

  protected <T> void assertAllEntriesInElasticsearchHaveAllDataWithCount(final String indexName,
                                                                         final Class<T> type,
                                                                         final int count,
                                                                         final Set<String> nullValueFields) {
    final List<T> savedDocs = databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(indexName, type);
    assertThat(savedDocs).hasSize(count)
      .allSatisfy(def -> assertThat(def).hasNoNullFieldsOrPropertiesExcept(nullValueFields.toArray(String[]::new)));
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

  protected boolean indicesExist(final List<IndexMappingCreator<?>> mappings) {
    return embeddedOptimizeExtension.getDatabaseSchemaManager()
      .indicesExist(embeddedOptimizeExtension.getOptimizeDatabaseClient(), mappings);
  }

}
