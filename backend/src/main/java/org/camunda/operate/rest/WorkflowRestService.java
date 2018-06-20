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

import static org.camunda.operate.rest.WorkflowRestService.WORKFLOW_URL;

import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.dto.WorkflowDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = WORKFLOW_URL)
@Profile("elasticsearch")
public class WorkflowRestService {

  @Autowired
  protected WorkflowReader workflowReader;

  public static final String WORKFLOW_URL = "/api/workflows";

  @GetMapping(path = "/{id}/xml")
  public String getWorkflowDiagram(@PathVariable("id") String workflowId) {
    return workflowReader.getDiagram(workflowId);
  }

  @GetMapping(path = "/{id}")
  public WorkflowDto getWorkflow(@PathVariable("id") String workflowId) {
    final WorkflowEntity workflowEntity = workflowReader.getWorkflow(workflowId);
    return WorkflowDto.createFrom(workflowEntity);
  }

}
