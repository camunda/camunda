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

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.processor.WorkflowInstanceLifecycle;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ElementInstance implements Serializable {

  private static final long serialVersionUID = 1L;

  private final IndexedRecord elementRecord;
  private final ElementInstance parent;

  private List<ElementInstance> children = new ArrayList<>();

  private long jobKey;

  private int activeTokens = 0;

  // records in this scope that have been stored for later reference
  private List<IndexedRecord> storedRecords = new ArrayList<>();

  public ElementInstance(long key, ElementInstance parent) {
    this.elementRecord = new IndexedRecord(key);
    this.parent = parent;

    if (this.parent != null) {
      this.parent.addChild(this);
    }
  }

  public long getKey() {
    return elementRecord.getKey();
  }

  public WorkflowInstanceIntent getState() {
    return elementRecord.getState();
  }

  public void setState(WorkflowInstanceIntent state) {
    this.elementRecord.setState(state);
  }

  public WorkflowInstanceRecord getValue() {
    return elementRecord.getValue();
  }

  public void setValue(WorkflowInstanceRecord value) {
    this.elementRecord.setValue(value);
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

  public List<IndexedRecord> getStoredRecords() {
    return storedRecords;
  }

  public void storeRecord(TypedRecord<WorkflowInstanceRecord> record) {
    final IndexedRecord indexedRecord = new IndexedRecord(record.getKey());
    indexedRecord.setState((WorkflowInstanceIntent) record.getMetadata().getIntent());
    indexedRecord.setValue(record.getValue());

    storedRecords.add(indexedRecord);
  }

  public boolean removeStoredRecords(long key) {
    return storedRecords.removeIf(r -> r.getKey() == key);
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
    return WorkflowInstanceLifecycle.canTerminate(getState());
  }

  public void spawnToken() {
    this.activeTokens += 1;
  }

  public void consumeToken() {
    this.activeTokens -= 1;
  }

  public int getNumberOfActiveTokens() {
    return activeTokens;
  }

  public int getNumberOfActiveElementInstances() {
    return children.size();
  }

  public int getNumberOfActiveExecutionPaths() {
    return activeTokens + getNumberOfActiveElementInstances();
  }
}
