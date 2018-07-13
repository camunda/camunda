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
package org.camunda.operate.rest.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.WorkflowEntity;
import io.swagger.annotations.ApiModel;

@ApiModel(value="Workflow group object", description = "Group of workflows with the same bpmnProcessId with all versions included")
public class WorkflowGroupDto {

  private String bpmnProcessId;

  private String name;

  private List<WorkflowDto> workflows;

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<WorkflowDto> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(List<WorkflowDto> workflows) {
    this.workflows = workflows;
  }

  public static List<WorkflowGroupDto> createFrom(Map<String, List<WorkflowEntity>> workflowsGrouped) {
    List<WorkflowGroupDto> groups = new ArrayList<>();
    workflowsGrouped.entrySet().stream().forEach(groupEntry -> {
        WorkflowGroupDto groupDto = new WorkflowGroupDto();
        groupDto.setBpmnProcessId(groupEntry.getKey());
        groupDto.setName(groupEntry.getValue().get(0).getName());
        groupDto.setWorkflows(WorkflowDto.createFrom(groupEntry.getValue()));
        groups.add(groupDto);
      }
    );
    groups.sort(new WorkflowGroupDto.WorkflowGroupComparator());
    return groups;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowGroupDto that = (WorkflowGroupDto) o;

    return bpmnProcessId != null ? bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId == null;
  }

  @Override
  public int hashCode() {
    return bpmnProcessId != null ? bpmnProcessId.hashCode() : 0;
  }

  public static class WorkflowGroupComparator implements Comparator<WorkflowGroupDto> {
    @Override
    public int compare(WorkflowGroupDto o1, WorkflowGroupDto o2) {

      //when sorting "name" field has higher priority than "bpmnProcessId" field
      if (o1.getName() == null && o2.getName() == null) {
        return o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
      }
      if (o1.getName() == null) {
        return 1;
      }
      if (o2.getName() == null) {
        return -1;
      }
      if (!o1.getName().equals(o2.getName())) {
        return o1.getName().compareTo(o2.getName());
      }
      return o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
    }
  }
}
