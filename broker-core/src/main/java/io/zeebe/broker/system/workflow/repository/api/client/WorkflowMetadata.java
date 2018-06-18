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
package io.zeebe.broker.system.workflow.repository.api.client;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class WorkflowMetadata extends UnpackedObject {
  private LongProperty workflowKeyProp = new LongProperty("workflowKey", -1);
  private IntegerProperty versionProp = new IntegerProperty("version", -1);
  private StringProperty topicNameProp = new StringProperty("topicName");
  private StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private StringProperty resourceNameProp = new StringProperty("resourceName");

  public WorkflowMetadata() {
    declareProperty(workflowKeyProp)
        .declareProperty(topicNameProp)
        .declareProperty(versionProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp);
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public WorkflowMetadata setWorkflowKey(long key) {
    workflowKeyProp.setValue(key);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public WorkflowMetadata setVersion(int version) {
    versionProp.setValue(version);
    return this;
  }

  public DirectBuffer getTopicName() {
    return topicNameProp.getValue();
  }

  public WorkflowMetadata setTopicName(DirectBuffer topicName) {
    topicNameProp.setValue(topicName);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public WorkflowMetadata setBpmnProcessId(DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowMetadata setBpmnProcessId(String value) {
    bpmnProcessIdProp.setValue(value);
    return this;
  }

  public WorkflowMetadata setTopicName(String topicName) {
    this.topicNameProp.setValue(topicName);
    return this;
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public WorkflowMetadata setResourceName(DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public WorkflowMetadata setResourceName(String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }
}
