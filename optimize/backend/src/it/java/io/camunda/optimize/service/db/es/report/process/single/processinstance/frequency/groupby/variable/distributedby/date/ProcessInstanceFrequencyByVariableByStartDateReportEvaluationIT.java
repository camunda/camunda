/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.date;
//
// import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
//
// public class ProcessInstanceFrequencyByVariableByStartDateReportEvaluationIT
//     extends AbstractProcessInstanceFrequencyByVariableByInstanceDateReportEvaluationIT {
//
//   @Override
//   protected ProcessReportDataType getTestReportDataType() {
//     return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_START_DATE;
//   }
//
//   @Override
//   protected DistributedByType getDistributeByType() {
//     return DistributedByType.START_DATE;
//   }
//
//   @Override
//   protected void changeProcessInstanceDate(
//       final String processInstanceId, final OffsetDateTime newDate) {
//     engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
//   }
// }
