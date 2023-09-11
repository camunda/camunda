/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateProcessInstanceIndexIT extends AbstractUpgrade311IT {

  private final String ALWAYS_COMPLETING_PROCESS = "always-completing-process";
  private final String ONLY_INCIDENT_PROCESS = "onlyincidentsprocess";
  private final String REVIEW_INVOICE_PROCESS = "reviewinvoice";

  @Test
  public void addTenantIdForProcessInstanceOfZeebeEngine() {
    // given
    executeBulk("steps/3.10/instances/310-process-instance-index-zeebe-data.json");

    // when
    performUpgrade();

    // then
    List<ProcessInstanceDto> alwaysCompletingProcessInstances = getAllDocumentsOfIndexAs(new ProcessInstanceIndex(
      ALWAYS_COMPLETING_PROCESS).getIndexName(), ProcessInstanceDto.class);
    assertThat(alwaysCompletingProcessInstances)
      .extracting(ProcessInstanceDto::getTenantId)
      .hasSize(4)
      .containsOnly("<default>");
    assertThat(alwaysCompletingProcessInstances)
      .flatExtracting(ProcessInstanceDto::getIncidents)
      .extracting(IncidentDto::getTenantId)
      .hasSize(4)
      .containsOnly("<default>");
    assertThat(alwaysCompletingProcessInstances)
      .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
      .extracting(FlowNodeInstanceDto::getTenantId)
      .hasSize(4)
      .containsOnly("<default>");

    List<ProcessInstanceDto> onlyIncidentsProcessInstances = getAllDocumentsOfIndexAs(new ProcessInstanceIndex(
      ONLY_INCIDENT_PROCESS).getIndexName(), ProcessInstanceDto.class);
    assertThat(onlyIncidentsProcessInstances)
      .extracting(ProcessInstanceDto::getTenantId)
      .hasSize(4)
      .containsOnly("<default>");
    assertThat(onlyIncidentsProcessInstances)
      .flatExtracting(ProcessInstanceDto::getIncidents)
      .extracting(IncidentDto::getTenantId)
      .hasSize(4)
      .containsOnly("<default>");
    assertThat(onlyIncidentsProcessInstances)
      .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
      .extracting(FlowNodeInstanceDto::getTenantId)
      .hasSize(8)
      .containsOnly("<default>");
  }

  @Test
  public void doNotAddTenantIdForProcessInstanceCambpmEngine() {
    // given
    executeBulk("steps/3.10/instances/310-process-instance-index-cambpm-data.json");

    // when
    performUpgrade();

    // then
    List<ProcessInstanceDto> instances = getAllDocumentsOfIndexAs(
      new ProcessInstanceIndex(REVIEW_INVOICE_PROCESS).getIndexName(),
      ProcessInstanceDto.class
    );
    assertThat(instances)
      .hasSize(2)
      .extracting(ProcessInstanceDto::getTenantId)
      .containsExactlyInAnyOrder(null, "someTenant");
    assertThat(instances)
      .flatExtracting(ProcessInstanceDto::getIncidents)
      .hasSize(4)
      .extracting(IncidentDto::getTenantId)
      .containsExactlyInAnyOrder(null, null, "someTenant", "someTenant");
    assertThat(instances)
      .flatExtracting(ProcessInstanceDto::getFlowNodeInstances)
      .hasSize(4)
      .extracting(FlowNodeInstanceDto::getTenantId)
      .containsExactlyInAnyOrder(null, null, "someTenant", "someTenant");
  }

}
