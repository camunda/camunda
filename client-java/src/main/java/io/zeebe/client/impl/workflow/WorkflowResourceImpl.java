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
package io.zeebe.client.impl.workflow;

import io.zeebe.client.api.commands.WorkflowResource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WorkflowResourceImpl implements WorkflowResource {
  private String bpmnProcessId;
  private int version;
  private long workflowKey;
  private String resourceName;
  private String bpmnXml;

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public WorkflowResourceImpl setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public WorkflowResourceImpl setVersion(int version) {
    this.version = version;
    return this;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getBpmnXml() {
    return bpmnXml;
  }

  public void setBpmnXml(String bpmnXml) {
    this.bpmnXml = bpmnXml;
  }

  @Override
  public InputStream getBpmnXmlAsStream() {
    final byte[] bytes = bpmnXml.getBytes(StandardCharsets.UTF_8);
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("WorkflowResource [bpmnProcessId=");
    builder.append(bpmnProcessId);
    builder.append(", version=");
    builder.append(version);
    builder.append(", workflowKey=");
    builder.append(workflowKey);
    builder.append(", resourceName=");
    builder.append(resourceName);
    builder.append(", bpmnXML=");
    builder.append(bpmnXml);
    builder.append("]");
    return builder.toString();
  }
}
