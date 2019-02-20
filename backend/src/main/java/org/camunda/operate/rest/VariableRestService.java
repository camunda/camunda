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

import java.util.List;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.reader.DetailViewReader;
import org.camunda.operate.rest.dto.detailview.VariableDto;
import org.camunda.operate.rest.dto.detailview.VariablesRequestDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import static org.camunda.operate.rest.VariableRestService.VARIABLE_URL;

@Api(tags = {"Workflow instance variables"})
@SwaggerDefinition(tags = {
  @Tag(name = "Workflow instance variables", description = "Workflow instance variables")
})
@RestController
@RequestMapping(value = VARIABLE_URL)
public class VariableRestService {

  public static final String VARIABLE_URL = "/api/variables";

  @Autowired
  private DetailViewReader detailViewReader;

  @ApiOperation("Query variables by workflow instance id and scope id")
  @PostMapping
  public List<VariableDto> getVariables(@RequestBody VariablesRequestDto variablesRequest) {
    if (variablesRequest.getWorkflowInstanceId() == null || variablesRequest.getScopeId() == null) {
      throw new InvalidRequestException("WorkflowInstanceId and ActivityInstanceId must be provided in the request.");
    }
    final List<VariableEntity> variableEntities = detailViewReader.getVariables(variablesRequest);
    return VariableDto.createFrom(variableEntities);
  }

}
