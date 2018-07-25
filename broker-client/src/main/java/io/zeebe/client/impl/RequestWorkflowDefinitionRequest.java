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
package io.zeebe.client.impl;

public class RequestWorkflowDefinitionRequest {
  private long workflowKey;
  private String topicName = "";
  private String bpmnProcessId = "";

  public long getWorkflowKey() {
    return workflowKey;
  }

  public void setWorkflowKey(long key) {
    this.workflowKey = key;
  }

  public String getTopicName() {
    return topicName;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }
}
