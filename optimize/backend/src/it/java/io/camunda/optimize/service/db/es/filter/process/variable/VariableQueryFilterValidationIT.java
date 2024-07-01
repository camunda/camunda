/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process.variable;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import io.camunda.optimize.service.db.es.filter.process.AbstractFilterIT;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import jakarta.ws.rs.core.Response;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class VariableQueryFilterValidationIT extends AbstractFilterIT {
//
//   @Test
//   public void validationExceptionOnNullValueField() {
//     // given
//     List<ProcessFilterDto<?>> variableFilterDto =
//         ProcessFilterBuilder.filter()
//             .variable()
//             .booleanType()
//             .values(null)
//             .name("foo")
//             .add()
//             .buildList();
//
//     // when
//     Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void validationExceptionOnNullNumericValuesField() {
//     // given
//     List<ProcessFilterDto<?>> variableFilterDto =
//         ProcessFilterBuilder.filter()
//             .variable()
//             .longType()
//             .operator(FilterOperator.IN)
//             .values(null)
//             .name("foo")
//             .add()
//             .buildList();
//
//     // when
//     Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void validationExceptionOnNullNameField() {
//     // given
//     List<ProcessFilterDto<?>> variableFilterDto =
//         ProcessFilterBuilder.filter().variable().booleanTrue().name(null).add().buildList();
//
//     // when
//     Response response = evaluateReportWithFilterAndGetResponse(variableFilterDto);
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   private Response evaluateReportWithFilterAndGetResponse(List<ProcessFilterDto<?>> filterList) {
//     final String TEST_DEFINITION_KEY = "testDefinition";
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(TEST_DEFINITION_KEY)
//             .setProcessDefinitionVersion("1")
//             .setReportDataType(ProcessReportDataType.RAW_DATA)
//             .build();
//     reportData.setFilter(filterList);
//     return reportClient.evaluateReportAndReturnResponse(reportData);
//   }
// }
