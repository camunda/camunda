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
package io.zeebe.model.bpmn.impl.metadata;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import io.zeebe.model.bpmn.instance.TaskDefinition;
import javax.xml.bind.annotation.XmlAttribute;
import org.agrona.DirectBuffer;

public class TaskDefinitionImpl extends BaseElement implements TaskDefinition {
  private DirectBuffer type;
  private int retries = DEFAULT_TASK_RETRIES;

  @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_TASK_TYPE)
  public void setType(String type) {
    this.type = wrapString(type);
  }

  public String getType() {
    return type != null ? bufferAsString(type) : null;
  }

  @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTES_TASK_RETRIES)
  public void setRetries(int reties) {
    this.retries = reties;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public DirectBuffer getTypeAsBuffer() {
    return type;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("TaskDefinition [type=");
    builder.append(getType());
    builder.append(", retries=");
    builder.append(retries);
    builder.append("]");
    return builder.toString();
  }
}
