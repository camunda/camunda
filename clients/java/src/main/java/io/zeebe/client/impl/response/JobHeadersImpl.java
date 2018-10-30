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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.JobHeaders;
import io.zeebe.gateway.protocol.GatewayOuterClass;

public class JobHeadersImpl implements JobHeaders {

  private final long workflowInstanceKey;
  private final String bpmnProcessId;
  private final int workflowDefinitionVersion;
  private final long workflowKey;
  private final String elementId;
  private final long elementInstanceKey;

  public JobHeadersImpl(GatewayOuterClass.JobHeaders jobHeaders) {
    workflowInstanceKey = jobHeaders.getWorkflowInstanceKey();
    bpmnProcessId = jobHeaders.getBpmnProcessId();
    workflowDefinitionVersion = jobHeaders.getWorkflowDefinitionVersion();
    workflowKey = jobHeaders.getWorkflowKey();
    elementId = jobHeaders.getElementId();
    elementInstanceKey = jobHeaders.getElementInstanceKey();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersion;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String toString() {
    return "JobHeadersImpl{"
        + "workflowInstanceKey="
        + workflowInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", workflowDefinitionVersion="
        + workflowDefinitionVersion
        + ", workflowKey="
        + workflowKey
        + ", activityId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + '}';
  }
}
