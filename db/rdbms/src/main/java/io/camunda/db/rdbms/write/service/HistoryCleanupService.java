/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.write.util.OffsetDateTimeUtil;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryCleanupService {

  protected static final String DEFAULT_HISTORY_TIME_TO_LIVE = "P30D";
  private static final Logger LOG = LoggerFactory.getLogger(HistoryCleanupService.class);
  private final ProcessInstanceWriter processInstanceWriter;
  private final IncidentWriter incidentWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final UserTaskWriter userTaskWriter;
  private final VariableWriter variableInstanceWriter;
  private final DecisionInstanceWriter decisionInstanceWriter;

  public HistoryCleanupService(
      final ProcessInstanceWriter processInstanceWriter,
      final IncidentWriter incidentWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final UserTaskWriter userTaskWriter,
      final VariableWriter variableInstanceWriter,
      final DecisionInstanceWriter decisionInstanceWriter) {
    this.processInstanceWriter = processInstanceWriter;
    this.incidentWriter = incidentWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.userTaskWriter = userTaskWriter;
    this.variableInstanceWriter = variableInstanceWriter;
    this.decisionInstanceWriter = decisionInstanceWriter;
  }

  public void scheduleProcessForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime endDate) {
    final OffsetDateTime historyCleanupDate = calculateHistoryCleanupDate(endDate);

    LOG.debug(
        "Scheduling process instance cleanup for key {} at {}",
        processInstanceKey,
        historyCleanupDate);
    processInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    flowNodeInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    incidentWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    userTaskWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    variableInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
    decisionInstanceWriter.scheduleForHistoryCleanup(processInstanceKey, historyCleanupDate);
  }

  public OffsetDateTime calculateHistoryCleanupDate(OffsetDateTime endDate) {
    return OffsetDateTimeUtil.addDuration(endDate, DEFAULT_HISTORY_TIME_TO_LIVE);
  }
}
