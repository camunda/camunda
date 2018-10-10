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

  private long workflowInstanceKey;
  private String bpmnProcessId;
  private int workflowDefinitionVersion;
  private long workflowKey;
  private String activityId;
  private long activityInstanceKey;

  public JobHeadersImpl(GatewayOuterClass.JobHeaders jobHeaders) {
    workflowInstanceKey = jobHeaders.getWorkflowInstanceKey();
    bpmnProcessId = jobHeaders.getBpmnProcessId();
    workflowDefinitionVersion = jobHeaders.getWorkflowDefinitionVersion();
    workflowKey = jobHeaders.getWorkflowKey();
    activityId = jobHeaders.getActivityId();
    activityInstanceKey = jobHeaders.getActivityInstanceKey();
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
  public String getActivityId() {
    return activityId;
  }

  @Override
  public long getActivityInstanceKey() {
    return activityInstanceKey;
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
        + activityId
        + '\''
        + ", activityInstanceKey="
        + activityInstanceKey
        + '}';
  }
}
