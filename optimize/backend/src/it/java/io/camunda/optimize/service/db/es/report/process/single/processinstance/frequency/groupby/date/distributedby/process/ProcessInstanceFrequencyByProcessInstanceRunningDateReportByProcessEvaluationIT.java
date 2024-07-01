/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.date.distributedby.process;
//
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
//
// public class ProcessInstanceFrequencyByProcessInstanceRunningDateReportByProcessEvaluationIT
//     extends AbstractProcessInstanceFrequencyByProcessInstanceDateByProcessReportEvaluationIT {
//
//   @Override
//   protected ProcessReportDataType getReportDataType() {
//     return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_RUNNING_DATE_BY_PROCESS;
//   }
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.RUNNING_DATE;
//   }
//
//   @Override
//   protected void changeProcessInstanceDate(
//       final ProcessInstanceEngineDto instanceEngineDto, final OffsetDateTime newDate) {
//     engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
//         instanceEngineDto.getId(), newDate, newDate);
//   }
// }
