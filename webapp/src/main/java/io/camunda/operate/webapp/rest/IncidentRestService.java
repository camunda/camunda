/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import java.util.Collection;
import io.camunda.operate.webapp.es.reader.IncidentStatisticsReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static io.camunda.operate.webapp.rest.IncidentRestService.INCIDENT_URL;

@Api(tags = {"Incidents statistics"})
@SwaggerDefinition(tags = {
  @Tag(name = "Incidents statistics", description = "Incidents statistics")
})
@RestController
@RequestMapping(value = INCIDENT_URL)
public class IncidentRestService {

  public static final String INCIDENT_URL = "/api/incidents";

  @Autowired
  private IncidentStatisticsReader incidentStatisticsReader;

  @ApiOperation("Get incident statistics for processes")
  @GetMapping("/byProcess")
  public Collection<IncidentsByProcessGroupStatisticsDto> getProcessAndIncidentsStatistics() {
    return incidentStatisticsReader.getProcessAndIncidentsStatistics();
  }

  @ApiOperation("Get incident statistics by error message")
  @GetMapping("/byError")
  public Collection<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {
    return incidentStatisticsReader.getIncidentStatisticsByError();
  }


}
