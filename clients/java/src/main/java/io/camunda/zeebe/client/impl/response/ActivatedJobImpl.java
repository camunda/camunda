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
package io.camunda.zeebe.client.impl.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
  private final String tenantId;
  private final String worker;
  private final int retries;
  private final long deadline;
  private final String variables;

  private Map<String, Object> variablesAsMap;

  public ActivatedJobImpl(final JsonMapper jsonMapper, final GatewayOuterClass.ActivatedJob job) {
    this.jsonMapper = jsonMapper;

    key = job.getKey();
    type = job.getType();

    // the default value of a string in Protobuf is an empty string, so this could fail if no
    // headers were given
    final String customHeaders = job.getCustomHeaders();
    this.customHeaders =
        customHeaders.isEmpty() ? new HashMap<>() : jsonMapper.fromJsonAsStringMap(customHeaders);
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
    tenantId = job.getTenantId();
  }

  public ActivatedJobImpl(
      final JsonMapper jsonMapper, final io.camunda.zeebe.client.protocol.rest.ActivatedJob job) {
    this.jsonMapper = jsonMapper;

    key = getOrEmpty(job.getJobKey());
    type = getOrEmpty(job.getType());
    customHeaders =
        job.getCustomHeaders() == null
            ? new HashMap<>()
            : job.getCustomHeaders().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        Entry::getKey,
                        e ->
                            (e.getValue() instanceof String)
                                ? (String) e.getValue()
                                : jsonMapper.toJson(e.getValue())));
    worker = getOrEmpty(job.getWorker());
    retries = getOrEmpty(job.getRetries());
    deadline = getOrEmpty(job.getDeadline());
    variablesAsMap = job.getVariables() == null ? new HashMap<>() : job.getVariables();
    variables = jsonMapper.toJson(variablesAsMap);
    processInstanceKey = getOrEmpty(job.getProcessInstanceKey());
    bpmnProcessId = getOrEmpty(job.getProcessDefinitionId());
    processDefinitionVersion = getOrEmpty(job.getProcessDefinitionVersion());
    processDefinitionKey = getOrEmpty(job.getProcessDefinitionKey());
    elementId = getOrEmpty(job.getElementId());
    elementInstanceKey = getOrEmpty(job.getElementInstanceKey());
    tenantId = getOrEmpty(job.getTenantId());
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
    if (variablesAsMap == null) {
      variablesAsMap = jsonMapper.fromJsonAsMap(variables);
    }
    return variablesAsMap;
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
  }

  @Override
  public Object getVariable(final String name) {
    final Map<String, Object> variables = getVariablesAsMap();
    if (!variables.containsKey(name)) {
      throw new ClientException(String.format("The variable %s is not available", name));
    }
    return getVariablesAsMap().get(name);
  }

  @Override
  public String toJson() {
    return jsonMapper.toJson(this);
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String toString() {
    return toJson();
  }

  private static String getOrEmpty(final String value) {
    return value == null ? "" : value;
  }

  private static Long getOrEmpty(final Long value) {
    return value == null ? -1L : value;
  }

  private static Integer getOrEmpty(final Integer value) {
    return value == null ? -1 : value;
  }
}
