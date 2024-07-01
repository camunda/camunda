/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package
// io.camunda.optimize.service.db.es.report.process.single.flownode.duration.groupby.date.distributedby.flownode;
//
// import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import java.time.OffsetDateTime;
// import java.util.Map;
//
// public class FlowNodeDurationByFlowNodeEndDateByFlowNodeReportEvaluationIT
//     extends FlowNodeDurationByFlowNodeDateByFlowNodeReportEvaluationIT {
//
//   @Override
//   protected ProcessGroupByType getGroupByType() {
//     return ProcessGroupByType.END_DATE;
//   }
//
//   @Override
//   protected ProcessReportDataType getReportDataType() {
//     return ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE;
//   }
//
//   @Override
//   protected void changeModelElementDates(final Map<String, OffsetDateTime> updates) {
//     engineDatabaseExtension.changeAllFlowNodeEndDates(updates);
//   }
//
//   @Override
//   protected void changeModelElementDate(
//       final ProcessInstanceEngineDto processInstance,
//       final String flowNodeId,
//       final OffsetDateTime dateToChangeTo) {
//     engineDatabaseExtension.changeFlowNodeEndDate(
//         processInstance.getId(), flowNodeId, dateToChangeTo);
//     engineDatabaseExtension.changeFlowNodeEndDate(
//         processInstance.getId(), flowNodeId, dateToChangeTo);
//   }
// }
