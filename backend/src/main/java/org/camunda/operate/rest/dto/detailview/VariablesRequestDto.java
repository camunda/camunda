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

public class VariablesRequestDto {

  private String workflowInstanceId;

  private String scopeId;

  public VariablesRequestDto() {
  }

  public VariablesRequestDto(String workflowInstanceId, String scopeId) {
    this.workflowInstanceId = workflowInstanceId;
    this.scopeId = scopeId;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VariablesRequestDto that = (VariablesRequestDto) o;

    if (workflowInstanceId != null ? !workflowInstanceId.equals(that.workflowInstanceId) : that.workflowInstanceId != null)
      return false;
    return scopeId != null ? scopeId.equals(that.scopeId) : that.scopeId == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstanceId != null ? workflowInstanceId.hashCode() : 0;
    result = 31 * result + (scopeId != null ? scopeId.hashCode() : 0);
    return result;
  }
}
