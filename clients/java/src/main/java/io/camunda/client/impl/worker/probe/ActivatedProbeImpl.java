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
import io.camunda.client.protocol.rest.ActivatedProbeResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActivatedProbeImpl implements ActivatedProbe {

  private final JsonMapper jsonMapper;
  private final long key;
  private final String probeId;
  private final String worker;
  private final long deadline;
  private final String tenantId;
  private final String physicalTenantId;
  private final List<String> secretPointers;
  private final Map<String, Object> input;
  private final SecretRedaction redaction;

  public ActivatedProbeImpl(final JsonMapper jsonMapper, final ActivatedProbeResult probe) {
    this.jsonMapper = jsonMapper;
    key = Long.parseLong(probe.getProbeKey());
    probeId = probe.getProbeId();
    worker = probe.getWorker();
    deadline = probe.getDeadline();
    tenantId = probe.getTenantId();
    physicalTenantId = probe.getPhysicalTenantId();
    secretPointers =
        probe.getSecretPointers() == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(probe.getSecretPointers());
    input = probe.getInput() == null ? Collections.emptyMap() : probe.getInput();
    redaction = new SecretRedaction(input, secretPointers);
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public String getProbeId() {
    return probeId;
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
  public String getPhysicalTenantId() {
    return physicalTenantId;
  }

  @Override
  public List<String> getSecretPointers() {
    return secretPointers;
  }

  @Override
  public String getInput() {
    return jsonMapper.toJson(input);
  }

  @Override
  public Map<String, Object> getInputAsMap() {
    return jsonMapper.fromJsonAsMap(getInput());
  }

  @Override
  public <T> T getInputAsType(final Class<T> inputType) {
    return jsonMapper.fromJson(getInput(), inputType);
  }

  @Override
  public String toJson() {
    return jsonMapper.toJson(asMap(input));
  }

  /** Prints the probe without the values the cluster injected into its input. */
  @Override
  public String toString() {
    return jsonMapper.toJson(asMap(redaction.redactedInput()));
  }

  /**
   * @return the text without the values the cluster injected into this probe
   */
  String withoutSecrets(final String text) {
    return redaction.removeFrom(text);
  }

  private Map<String, Object> asMap(final Object input) {
    final Map<String, Object> json = new LinkedHashMap<>();
    json.put("key", key);
    json.put("probeId", probeId);
    json.put("worker", worker);
    json.put("deadline", deadline);
    json.put("tenantId", tenantId);
    json.put("physicalTenantId", physicalTenantId);
    json.put("secretPointers", secretPointers);
    json.put("input", input);
    return json;
  }
}
