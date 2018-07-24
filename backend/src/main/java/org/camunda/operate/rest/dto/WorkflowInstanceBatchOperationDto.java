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
import java.util.List;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.rest.exception.InvalidRequestException;

public class WorkflowInstanceBatchOperationDto {

  public WorkflowInstanceBatchOperationDto() {
  }

  public WorkflowInstanceBatchOperationDto(List<WorkflowInstanceQueryDto> queries) {
    this.queries = queries;
  }

  private List<WorkflowInstanceQueryDto> queries = new ArrayList<>();

  private OperationType operationType;

  public List<WorkflowInstanceQueryDto> getQueries() {
    return queries;
  }

  public void setQueries(List<WorkflowInstanceQueryDto> queries) {
    this.queries = queries;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(OperationType operationType) {
    this.operationType = operationType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    WorkflowInstanceBatchOperationDto that = (WorkflowInstanceBatchOperationDto) o;

    if (queries != null ? !queries.equals(that.queries) : that.queries != null)
      return false;
    return operationType == that.operationType;
  }

  @Override
  public int hashCode() {
    int result = queries != null ? queries.hashCode() : 0;
    result = 31 * result + (operationType != null ? operationType.hashCode() : 0);
    return result;
  }
}
