/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collection;
import io.camunda.operate.webapp.es.reader.IncidentStatisticsReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static io.camunda.operate.webapp.rest.IncidentRestService.INCIDENT_URL;

@Tag(name = "Incidents statistics")
@RestController
@RequestMapping(value = INCIDENT_URL)
public class IncidentRestService {

  public static final String INCIDENT_URL = "/api/incidents";

  @Autowired
  private IncidentStatisticsReader incidentStatisticsReader;

  @Operation(summary = "Get incident statistics for processes")
  @GetMapping("/byProcess")
  public Collection<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics() {
    return incidentStatisticsReader.getProcessAndIncidentsStatistics();
  }

  @Operation(summary = "Get incident statistics by error message")
  @GetMapping("/byError")
  public Collection<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {
    return incidentStatisticsReader.getIncidentStatisticsByError();
  }


}
