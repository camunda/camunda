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
import java.util.LinkedHashMap;
import java.util.Map;

public final class ActivatedProbeImpl implements ActivatedProbe {

  private final JsonMapper jsonMapper;
  private final long key;
  private final String probeId;
  private final String worker;
  private final long deadline;
  private final String tenantId;
  private final String physicalTenantId;
  private final String input;

  /** Reads one probe of the {@code POST /v2/probes/activation} response. */
  public ActivatedProbeImpl(final JsonMapper jsonMapper, final Map<?, ?> probe) {
    this.jsonMapper = jsonMapper;
    key = Long.parseLong(String.valueOf(probe.get("probeKey")));
    probeId = text(probe.get("probeId"));
    worker = text(probe.get("worker"));
    deadline = probe.get("deadline") == null ? -1 : ((Number) probe.get("deadline")).longValue();
    tenantId = text(probe.get("tenantId"));
    physicalTenantId = text(probe.get("physicalTenantId"));
    input = probe.get("input") == null ? "{}" : jsonMapper.toJson(probe.get("input"));
  }

  private static String text(final Object value) {
    return value == null ? "" : String.valueOf(value);
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
    json.put("probeId", probeId);
    json.put("worker", worker);
    json.put("deadline", deadline);
    json.put("tenantId", tenantId);
    json.put("physicalTenantId", physicalTenantId);
    json.put("input", getInputAsMap());
    return jsonMapper.toJson(json);
  }

  /** Leaves out the input: it carries the injected secret values. */
  @Override
  public String toString() {
    return String.format(
        "ActivatedProbe{key=%d, probeId='%s', worker='%s', tenantId='%s', physicalTenantId='%s'}",
        key, probeId, worker, tenantId, physicalTenantId);
  }
}
