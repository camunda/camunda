/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZeebeIncidentImportServiceLateIncidentTest {

  @Test
  void shouldOnlyReportIncidentsTheAgentRunImportAlreadyPassed() {
    // when the agent run import fetched up to position 100
    final Map<String, String> late =
        ZeebeIncidentImportService.lateIncidentProcessInstances(
            List.of(incident(90, 1L, "loan"), incident(100, 2L, "chat"), incident(110, 3L, "x")),
            100);

    // then
    assertThat(late).containsOnly(Map.entry("1", "loan"), Map.entry("2", "chat"));
  }

  @Test
  void shouldFindNoLateIncidentInNormalOperation() {
    // the agent run import waits for the incident import, so it is behind
    assertThat(
            ZeebeIncidentImportService.lateIncidentProcessInstances(
                List.of(incident(60, 1L, "loan")), 50))
        .isEmpty();
  }

  private static ZeebeIncidentRecordDto incident(
      final long position, final long processInstanceKey, final String bpmnProcessId) {
    final ZeebeIncidentDataDto value = new ZeebeIncidentDataDto();
    value.setProcessInstanceKey(processInstanceKey);
    value.setBpmnProcessId(bpmnProcessId);
    final ZeebeIncidentRecordDto record = new ZeebeIncidentRecordDto();
    record.setPosition(position);
    record.setValue(value);
    return record;
  }
}
