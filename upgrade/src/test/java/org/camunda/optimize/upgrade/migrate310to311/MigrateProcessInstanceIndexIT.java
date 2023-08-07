/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
    List<IncidentDto> alwaysCompletingInstanceIncidentDtos = alwaysCompletingProcessInstances.stream()
      .map(ProcessInstanceDto::getIncidents)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    assertThat(alwaysCompletingProcessInstances)
      .hasSize(4)
      .extracting(ProcessInstanceDto::getTenantId)
      .containsExactlyInAnyOrder("<default>", "<default>", "<default>", "<default>");
    assertThat(alwaysCompletingInstanceIncidentDtos)
      .hasSize(4)
      .extracting(IncidentDto::getTenantId)
      .containsExactlyInAnyOrder("<default>", "<default>", "<default>", "<default>");

    List<ProcessInstanceDto> onlyIncidentsProcessInstances = getAllDocumentsOfIndexAs(new ProcessInstanceIndex(
      ONLY_INCIDENT_PROCESS).getIndexName(), ProcessInstanceDto.class);
    List<IncidentDto> onlyIncidentsInstanceIncidentDtos = alwaysCompletingProcessInstances.stream()
      .map(ProcessInstanceDto::getIncidents)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    assertThat(onlyIncidentsProcessInstances)
      .hasSize(4)
      .extracting(ProcessInstanceDto::getTenantId)
      .containsExactlyInAnyOrder("<default>", "<default>", "<default>", "<default>");
    assertThat(onlyIncidentsInstanceIncidentDtos)
      .hasSize(4)
      .extracting(IncidentDto::getTenantId)
      .containsExactlyInAnyOrder("<default>", "<default>", "<default>", "<default>");
  }

  @Test
  public void doNotAddTenantIdForProcessInstanceCambpmEngine() {
    // given
    executeBulk("steps/3.10/instances/310-process-instance-index-cambpm-data.json");

    // when
    performUpgrade();

    // then
    List<ProcessInstanceDto> reviewinvoiceProcessInstances = getAllDocumentsOfIndexAs(new ProcessInstanceIndex(
      REVIEW_INVOICE_PROCESS).getIndexName(), ProcessInstanceDto.class);
    List<IncidentDto> incidentDtos = reviewinvoiceProcessInstances.stream()
      .map(ProcessInstanceDto::getIncidents)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
    assertThat(reviewinvoiceProcessInstances)
      .hasSize(2)
      .extracting(ProcessInstanceDto::getTenantId)
      .containsExactlyInAnyOrder(null, "someTenant");
    assertThat(incidentDtos)
      .hasSize(4)
      .extracting(IncidentDto::getTenantId)
      .containsExactlyInAnyOrder(null, null, "someTenant", "someTenant");
  }

}
