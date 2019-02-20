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
package org.camunda.operate.zeebeimport.record.value;

import org.camunda.operate.zeebeimport.record.RecordValueImpl;
import io.zeebe.exporter.record.value.VariableRecordValue;

public class VariableRecordValueImpl extends RecordValueImpl implements VariableRecordValue {

  private String name;
  private String value;
  private long scopeKey;
  private long workflowInstanceKey;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setScopeKey(long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VariableRecordValueImpl that = (VariableRecordValueImpl) o;

    if (scopeKey != that.scopeKey)
      return false;
    if (workflowInstanceKey != that.workflowInstanceKey)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return value != null ? value.equals(that.value) : that.value == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    result = 31 * result + (int) (scopeKey ^ (scopeKey >>> 32));
    result = 31 * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "VariableRecordValueImpl{" + "name='" + name + '\'' + ", value='" + value + '\'' + ", scopeKey=" + scopeKey + ", workflowInstanceKey="
      + workflowInstanceKey + "} " + super.toString();
  }
}
