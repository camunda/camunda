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
package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.model.bpmn.BpmnAspect;
import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.FlowElement;
import javax.xml.bind.annotation.*;
import org.agrona.DirectBuffer;

public class FlowElementImpl extends BaseElement implements FlowElement {
  private DirectBuffer id;
  private DirectBuffer name;

  private ExtensionElementsImpl extensionElements;

  private BpmnAspect bpmnAspect = BpmnAspect.NONE;

  @XmlID
  @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_ID, required = true)
  public void setId(String id) {
    this.id = wrapString(id);
  }

  public String getId() {
    return id != null ? bufferAsString(id) : null;
  }

  @XmlAttribute(name = BpmnConstants.BPMN_ATTRIBUTE_NAME)
  public void setName(String name) {
    this.name = wrapString(name);
  }

  public String getName() {
    return name != null ? bufferAsString(name) : null;
  }

  @XmlElement(
      name = BpmnConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS,
      namespace = BpmnConstants.BPMN20_NS)
  public void setExtensionElements(ExtensionElementsImpl extensionElements) {
    this.extensionElements = extensionElements;
  }

  public ExtensionElementsImpl getExtensionElements() {
    return extensionElements;
  }

  @Override
  public DirectBuffer getIdAsBuffer() {
    return id;
  }

  public DirectBuffer getNameAsBuffer() {
    return name;
  }

  @Override
  public BpmnAspect getBpmnAspect() {
    return bpmnAspect;
  }

  @XmlTransient
  public void setBpmnAspect(BpmnAspect bpmnAspect) {
    this.bpmnAspect = bpmnAspect;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("FlowElement [id=");
    builder.append(getId());
    builder.append(", name=");
    builder.append(getName());
    builder.append("]");
    return builder.toString();
  }
}
