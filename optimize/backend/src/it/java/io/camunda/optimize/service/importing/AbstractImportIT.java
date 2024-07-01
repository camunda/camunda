/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing;
//
// import static io.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;
// import static io.camunda.optimize.util.SuppressionConstants.UNUSED;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.db.schema.IndexMappingCreator;
// import io.camunda.optimize.test.it.extension.ErrorResponseMock;
// import io.camunda.optimize.test.it.extension.MockServerUtil;
// import io.camunda.optimize.util.BpmnModels;
// import jakarta.ws.rs.core.Response;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.stream.Stream;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
// import org.junit.jupiter.api.Tag;
// import org.mockserver.model.HttpResponse;
//
// @Tag("import")
// public abstract class AbstractImportIT extends AbstractPlatformIT {
//
//   protected <T> void assertAllEntriesInElasticsearchHaveAllData(
//       String elasticsearchIndex, final Class<T> type, final Set<String> excludedFields) {
//     assertAllEntriesInElasticsearchHaveAllDataWithCount(
//         elasticsearchIndex, type, 1, excludedFields);
//   }
//
//   protected <T> void assertAllEntriesInElasticsearchHaveAllDataWithCount(
//       final String elasticsearchIndex, final Class<T> type, final int count) {
//     assertAllEntriesInElasticsearchHaveAllDataWithCount(
//         elasticsearchIndex, type, count, Collections.emptySet());
//   }
//
//   protected <T> void assertAllEntriesInElasticsearchHaveAllDataWithCount(
//       final String indexName,
//       final Class<T> type,
//       final int count,
//       final Set<String> nullValueFields) {
//     final List<T> savedDocs =
//         databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(indexName, type);
//     assertThat(savedDocs)
//         .hasSize(count)
//         .allSatisfy(
//             def ->
//                 assertThat(def)
//                     .hasNoNullFieldsOrPropertiesExcept(nullValueFields.toArray(String[]::new)));
//   }
//
//   protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
//     Map<String, Object> variables = new HashMap<>();
//     variables.put("aVariable", "aStringVariables");
//     return deployAndStartSimpleServiceProcessTaskWithVariables(variables);
//   }
//
//   protected ProcessInstanceEngineDto deployAndStartSimpleServiceProcessTaskWithVariables(
//       Map<String, Object> variables) {
//     BpmnModelInstance processModel = getSingleServiceTaskProcess();
//     return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel,
// variables);
//   }
//
//   protected ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
//     Map<String, Object> variables = new HashMap<>();
//     variables.put("aVariable", "aStringVariable");
//     return engineIntegrationExtension.deployAndStartProcessWithVariables(
//         BpmnModels.getSingleUserTaskDiagram(), variables);
//   }
//
//   protected static Stream<ErrorResponseMock> engineErrors() {
//     return MockServerUtil.engineMockedErrorResponses();
//   }
//
//   @SuppressWarnings(UNUSED)
//   protected static Stream<ErrorResponseMock> engineAuthorizationErrors() {
//     return Stream.of(
//         (request, times, mockServer) ->
//             mockServer
//                 .when(request, times)
//                 .respond(
//                     HttpResponse.response()
//                         .withStatusCode(Response.Status.NOT_FOUND.getStatusCode())),
//         (request, times, mockServer) ->
//             mockServer
//                 .when(request, times)
//                 .respond(
//                     HttpResponse.response()
//                         .withStatusCode(Response.Status.FORBIDDEN.getStatusCode())));
//   }
//
//   protected boolean indicesExist(final List<IndexMappingCreator<?>> mappings) {
//     return embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .indicesExist(embeddedOptimizeExtension.getOptimizeDatabaseClient(), mappings);
//   }
// }
