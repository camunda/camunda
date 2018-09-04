/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.rest;

import java.util.Collection;
import org.camunda.operate.es.reader.IncidentStatisticsReader;
import org.camunda.operate.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.rest.IncidentRestService.INCIDENT_URL;

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

  @ApiOperation("Get incident statistics for workflows")
  @GetMapping("/byWorkflow")
  public Collection<IncidentsByWorkflowGroupStatisticsDto> getIncidentStatisticsByWorkflow() {
    return incidentStatisticsReader.getIncidentStatisticsByWorkflow();
  }

  @ApiOperation("Get incident statistics by error message")
  @GetMapping("/byError")
  public Collection<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {
    return incidentStatisticsReader.getIncidentStatisticsByError();
  }


}
