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
package org.camunda.operate.rest.dto.detailview;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.VariableEntity;

public class VariableDto {

  private String id;
  private String name;
  private String value;
  private String scopeId;
  private String workflowInstanceId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }


  public static VariableDto createFrom(VariableEntity variableEntity) {
    if (variableEntity == null) {
      return null;
    }
    VariableDto variable = new VariableDto();
    variable.setId(variableEntity.getId());
    variable.setName(variableEntity.getName());
    variable.setValue(variableEntity.getValue());
    variable.setScopeId(variableEntity.getScopeId());
    variable.setWorkflowInstanceId(variableEntity.getWorkflowInstanceId());
    return variable;
  }

  public static List<VariableDto> createFrom(List<VariableEntity> variableEntities) {
    List<VariableDto> result = new ArrayList<>();
    if (variableEntities != null) {
      for (VariableEntity variableEntity: variableEntities) {
        if (variableEntity != null) {
          result.add(createFrom(variableEntity));
        }
      }
    }
    return result;
  }
}
