/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.process.combined;
//
// import static
// io.camunda.optimize.service.util.ProcessReportDataType.FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION;
//
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
//
// public class CombinedFlowNodeFrequencyByDurationReportIT extends AbstractCombinedDurationReportIT
// {
//
//   @Override
//   protected void startInstanceAndModifyRelevantDurations(
//       final String definitionId, final int durationInMillis) {
//     final ProcessInstanceEngineDto processInstance =
//         engineIntegrationExtension.startProcessInstance(definitionId);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());
//     engineDatabaseExtension.changeAllFlowNodeTotalDurations(
//         processInstance.getId(), durationInMillis);
//   }
//
//   @Override
//   protected ProcessReportDataType getReportDataType() {
//     return FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE_DURATION;
//   }
// }
