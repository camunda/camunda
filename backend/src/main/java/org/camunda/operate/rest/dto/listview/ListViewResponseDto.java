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
package org.camunda.operate.rest.dto.listview;

import java.util.ArrayList;
import java.util.List;

public class ListViewResponseDto {

  private List<ListViewWorkflowInstanceDto> workflowInstances = new ArrayList<>();

  private long totalCount;

  public List<ListViewWorkflowInstanceDto> getWorkflowInstances() {
    return workflowInstances;
  }

  public void setWorkflowInstances(List<ListViewWorkflowInstanceDto> workflowInstances) {
    this.workflowInstances = workflowInstances;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewResponseDto that = (ListViewResponseDto) o;

    if (totalCount != that.totalCount)
      return false;
    return workflowInstances != null ? workflowInstances.equals(that.workflowInstances) : that.workflowInstances == null;
  }

  @Override
  public int hashCode() {
    int result = workflowInstances != null ? workflowInstances.hashCode() : 0;
    result = 31 * result + (int) (totalCount ^ (totalCount >>> 32));
    return result;
  }
}
