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

import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class WorkflowMetadataAndResource extends WorkflowMetadata {
  private final StringProperty bpmnXmlProp = new StringProperty("bpmnXml");

  public WorkflowMetadataAndResource() {
    super();
    declareProperty(bpmnXmlProp);
  }

  public DirectBuffer getBpmnXml() {
    return bpmnXmlProp.getValue();
  }

  public WorkflowMetadataAndResource setBpmnXml(final String bpmnXml) {
    bpmnXmlProp.setValue(bpmnXml);
    return this;
  }

  public WorkflowMetadataAndResource setBpmnXml(final DirectBuffer val) {
    bpmnXmlProp.setValue(val);
    return this;
  }
}
