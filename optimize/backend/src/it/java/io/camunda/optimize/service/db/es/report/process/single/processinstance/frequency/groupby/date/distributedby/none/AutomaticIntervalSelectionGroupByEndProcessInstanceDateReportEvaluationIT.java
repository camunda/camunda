/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;
//
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import java.time.OffsetDateTime;
// import java.time.ZonedDateTime;
// import java.util.Map;
//
// public class AutomaticIntervalSelectionGroupByEndProcessInstanceDateReportEvaluationIT
//     extends AbstractAutomaticIntervalSelectionGroupByProcessInstanceDateReportEvaluationIT {
//
//   @Override
//   protected ProcessReportDataDto getGroupByDateReportData(String key, String version) {
//     return TemplatedProcessReportDataBuilder.createReportData()
//         .setProcessDefinitionKey(key)
//         .setProcessDefinitionVersion(version)
//         .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
//         .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE)
//         .build();
//   }
//
//   @Override
//   protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> updates) {
//     engineDatabaseExtension.changeProcessInstanceEndDates(updates);
//   }
//
//   @Override
//   protected void updateProcessInstanceDate(
//       final ZonedDateTime min, final ProcessInstanceEngineDto procInstMin) {
//     engineDatabaseExtension.changeProcessInstanceEndDate(
//         procInstMin.getId(), min.toOffsetDateTime());
//   }
// }
