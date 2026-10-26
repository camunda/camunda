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
package io.camunda.client.impl.worker.probe;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.probe.ActivatedProbe;
import io.camunda.client.api.worker.probe.ProbeOperation;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActivatedProbeImpl implements ActivatedProbe {

  private final JsonMapper jsonMapper;
  private final long key;
  private final ProbeOperation operation;
  private final String template;
  private final String worker;
  private final long deadline;
  private final String tenantId;
  private final String input;

  public ActivatedProbeImpl(
      final JsonMapper jsonMapper, final GatewayOuterClass.ActivatedProbe probe) {
    this.jsonMapper = jsonMapper;
    key = probe.getKey();
    operation = ProbeOperation.valueOf(probe.getOperation());
    template = probe.getTemplate();
    worker = probe.getWorker();
    deadline = probe.getDeadline();
    tenantId = probe.getTenantId();
    input = probe.getInput().isEmpty() ? "{}" : probe.getInput();
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public ProbeOperation getOperation() {
    return operation;
  }

  @Override
  public String getTemplate() {
    return template;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getInput() {
    return input;
  }

  @Override
  public Map<String, Object> getInputAsMap() {
    return jsonMapper.fromJsonAsMap(input);
  }

  @Override
  public <T> T getInputAsType(final Class<T> inputType) {
    return jsonMapper.fromJson(input, inputType);
  }

  @Override
  public String toJson() {
    final Map<String, Object> json = new LinkedHashMap<>();
    json.put("key", key);
    json.put("operation", operation);
    json.put("template", template);
    json.put("worker", worker);
    json.put("deadline", deadline);
    json.put("tenantId", tenantId);
    json.put("input", getInputAsMap());
    return jsonMapper.toJson(json);
  }

  /** Leaves out the input: it carries the injected secret values. */
  @Override
  public String toString() {
    return String.format(
        "ActivatedProbe{key=%d, operation=%s, template='%s', worker='%s', tenantId='%s'}",
        key, operation, template, worker, tenantId);
  }
}
