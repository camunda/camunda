/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.date.distributedby.variable;
//
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
//
// public class ProcessInstanceFrequencyByInstanceEndDateByVariableReportEvaluationIT
//     extends AbstractProcessInstanceFrequencyByInstanceDateByVariableReportEvaluationIT {
//   @Override
//   protected ProcessReportDataType getTestReportDataType() {
//     return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE_BY_VARIABLE;
//   }
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.END_DATE;
//   }
//
//   @Override
//   protected void changeProcessInstanceDate(
//       final String processInstanceId, final OffsetDateTime newDate) {
//     engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
//   }
// }
