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
package io.zeebe.broker.workflow.index;

import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import org.agrona.ExpandableDirectByteBuffer;

public class ElementInstance {

  private final long key;
  private final ElementInstance parent;

  private WorkflowInstanceIntent state;

  private ExpandableDirectByteBuffer valueBuffer = new ExpandableDirectByteBuffer();
  private WorkflowInstanceRecord value = new WorkflowInstanceRecord();

  private List<ElementInstance> children = new ArrayList<>();

  private long jobKey;

  public ElementInstance(long key, ElementInstance parent) {
    this.key = key;
    this.parent = parent;

    if (this.parent != null) {
      this.parent.addChild(this);
    }
  }

  public long getKey() {
    return key;
  }

  public WorkflowInstanceIntent getState() {
    return state;
  }

  public void setState(WorkflowInstanceIntent state) {
    this.state = state;
  }

  public WorkflowInstanceRecord getValue() {
    return value;
  }

  public void setValue(WorkflowInstanceRecord value) {
    final int encodedLength = value.getLength();
    valueBuffer.checkLimit(encodedLength);
    value.write(valueBuffer, 0);

    this.value.wrap(valueBuffer, 0, encodedLength);
  }

  public ElementInstance getParent() {
    return parent;
  }

  public List<ElementInstance> getChildren() {
    return children;
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  private void addChild(ElementInstance scopeInstance) {
    this.children.add(scopeInstance);
  }

  private void removeChild(ElementInstance scopeInstance) {
    this.children.remove(scopeInstance);
  }

  public void destroy() {
    if (this.parent != null) {
      this.parent.removeChild(this);
    }
  }

  public boolean canTerminate() {
    return state == WorkflowInstanceIntent.ACTIVITY_READY
        || state == WorkflowInstanceIntent.ACTIVITY_ACTIVATED
        || state == WorkflowInstanceIntent.ACTIVITY_COMPLETING;
  }
}
