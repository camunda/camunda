/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.process;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
// import static
// io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.LESS_THAN;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import jakarta.ws.rs.core.Response;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class DurationFilterIT extends AbstractDurationFilterIT {
//
//   @Test
//   public void testGetReportWithMixedDurationCriteria() {
//     // given
//     long daysToShift = 0L;
//     long durationInSec = 2L;
//
//     ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);
//
//     // when
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
//             .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
//             .setReportDataType(ProcessReportDataType.RAW_DATA)
//             .build();
//     List<ProcessFilterDto<?>> gte =
//         ProcessFilterBuilder.filter()
//             .duration()
//             .unit(DurationUnit.SECONDS)
//             .value((long) 2)
//             .operator(GREATER_THAN_EQUALS)
//             .add()
//             .buildList();
//     List<ProcessFilterDto<?>> lt =
//         ProcessFilterBuilder.filter()
//             .duration()
//             .unit(DurationUnit.DAYS)
//             .value((long) 1)
//             .operator(LESS_THAN)
//             .add()
//             .buildList();
//     gte.addAll(lt);
//     reportData.setFilter(gte);
//     AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
//         reportClient.evaluateRawReport(reportData);
//
//     // then
//     assertResult(processInstance, result);
//   }
//
//   @Test
//   public void testValidationExceptionOnNullFilterField() {
//     // given
//     ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
//     importAllEngineEntitiesFromScratch();
//
//     ProcessReportDataDto reportData =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
//             .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
//             .setReportDataType(ProcessReportDataType.RAW_DATA)
//             .build();
//     reportData.setFilter(
//         ProcessFilterBuilder.filter()
//             .duration()
//             .unit(null)
//             .value((long) 2)
//             .operator(GREATER_THAN_EQUALS)
//             .add()
//             .buildList());
//
//     assertThat(reportClient.evaluateReportAndReturnResponse(reportData).getStatus())
//         .isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
//   }
// }
