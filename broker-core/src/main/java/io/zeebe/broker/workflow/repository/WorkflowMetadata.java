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
package io.zeebe.broker.workflow.repository;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class WorkflowMetadata extends UnpackedObject {
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1);
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");

  public WorkflowMetadata() {
    declareProperty(workflowKeyProp)
        .declareProperty(versionProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp);
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public WorkflowMetadata setWorkflowKey(final long key) {
    workflowKeyProp.setValue(key);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public WorkflowMetadata setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public WorkflowMetadata setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowMetadata setBpmnProcessId(final String value) {
    bpmnProcessIdProp.setValue(value);
    return this;
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public WorkflowMetadata setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public WorkflowMetadata setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }
}
