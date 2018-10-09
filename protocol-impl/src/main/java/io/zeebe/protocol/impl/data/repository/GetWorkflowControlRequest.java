/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.data.repository;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class GetWorkflowControlRequest extends UnpackedObject {
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1);
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");

  public GetWorkflowControlRequest() {
    declareProperty(workflowKeyProp)
        .declareProperty(versionProp)
        .declareProperty(bpmnProcessIdProp);
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public GetWorkflowControlRequest setWorkflowKey(final long key) {
    workflowKeyProp.setValue(key);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public GetWorkflowControlRequest setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public GetWorkflowControlRequest setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }
}
