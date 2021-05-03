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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.Map;

public final class ActivatedJobImpl implements ActivatedJob {

  @JsonIgnore private final JsonMapper jsonMapper;

  private final long key;
  private final String type;
  private final Map<String, String> customHeaders;
  private final long processInstanceKey;
  private final String bpmnProcessId;
  private final int processDefinitionVersion;
  private final long processDefinitionKey;
  private final String elementId;
  private final long elementInstanceKey;
  private final String worker;
  private final int retries;
  private final long deadline;
  private final String variables;

  public ActivatedJobImpl(final JsonMapper jsonMapper, final GatewayOuterClass.ActivatedJob job) {
    this.jsonMapper = jsonMapper;

    key = job.getKey();
    type = job.getType();
    customHeaders = jsonMapper.fromJsonAsStringMap(job.getCustomHeaders());
    worker = job.getWorker();
    retries = job.getRetries();
    deadline = job.getDeadline();
    variables = job.getVariables();
    processInstanceKey = job.getProcessInstanceKey();
    bpmnProcessId = job.getBpmnProcessId();
    processDefinitionVersion = job.getProcessDefinitionVersion();
    processDefinitionKey = job.getProcessDefinitionKey();
    elementId = job.getElementId();
    elementInstanceKey = job.getElementInstanceKey();
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
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
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    return jsonMapper.fromJsonAsMap(variables);
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
  }

  @Override
  public String toJson() {
    return jsonMapper.toJson(this);
  }

  @Override
  public String toString() {
    return toJson();
  }
}
