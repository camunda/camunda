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
package io.zeebe.client.impl.events;

import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WorkflowResourceImpl extends WorkflowImpl implements WorkflowResource {

  private final String bpmnXml;

  public WorkflowResourceImpl(GetWorkflowResponse response) {
    this(
        response.getWorkflowKey(),
        response.getBpmnProcessId(),
        response.getVersion(),
        response.getResourceName(),
        response.getBpmnXml());
  }

  public WorkflowResourceImpl(
      long workflowKey, String bpmnProcessId, int version, String resourceName, String bpmnXml) {
    super(workflowKey, bpmnProcessId, version, resourceName);
    this.bpmnXml = bpmnXml;
  }

  @Override
  public String getBpmnXml() {
    return bpmnXml;
  }

  @Override
  public InputStream getBpmnXmlAsStream() {
    final byte[] bytes = bpmnXml.getBytes(StandardCharsets.UTF_8);
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public String toString() {
    return "WorkflowResourceImpl{"
        + "workflowKey="
        + workflowKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", resourceName='"
        + resourceName
        + '\''
        + ", bpmnXml='"
        + bpmnXml
        + '\''
        + '}';
  }
}
