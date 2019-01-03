/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.camunda.operate.zeebeimport.record.value;

import java.util.Objects;
import org.camunda.operate.zeebeimport.record.RecordValueWithPayloadImpl;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.BpmnElementType;

public class WorkflowInstanceRecordValueImpl extends RecordValueWithPayloadImpl
    implements WorkflowInstanceRecordValue {
  private String bpmnProcessId;
  private String elementId;
  private int version;
  private long workflowKey;
  private long workflowInstanceKey;
  private long scopeInstanceKey;
  private BpmnElementType bpmnElementType;

  public WorkflowInstanceRecordValueImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getScopeInstanceKey() {
    return scopeInstanceKey;
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    WorkflowInstanceRecordValueImpl that = (WorkflowInstanceRecordValueImpl) o;

    if (version != that.version)
      return false;
    if (workflowKey != that.workflowKey)
      return false;
    if (workflowInstanceKey != that.workflowInstanceKey)
      return false;
    if (scopeInstanceKey != that.scopeInstanceKey)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (elementId != null ? !elementId.equals(that.elementId) : that.elementId != null)
      return false;
    return bpmnElementType == that.bpmnElementType;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (int) (workflowKey ^ (workflowKey >>> 32));
    result = 31 * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
    result = 31 * result + (int) (scopeInstanceKey ^ (scopeInstanceKey >>> 32));
    result = 31 * result + (bpmnElementType != null ? bpmnElementType.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "WorkflowInstanceRecordValueImpl{" + "bpmnProcessId='" + bpmnProcessId + '\'' + ", elementId='" + elementId + '\'' + ", version=" + version
      + ", workflowKey=" + workflowKey + ", workflowInstanceKey=" + workflowInstanceKey + ", scopeInstanceKey=" + scopeInstanceKey + ", bpmnElementType="
      + bpmnElementType + "} " + super.toString();
  }
}
