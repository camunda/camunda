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
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.response.JobHeaders;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import java.time.Instant;
import java.util.Map;

public class ActivatedJobImpl implements ActivatedJob {

  @JsonIgnore private final ZeebeObjectMapper objectMapper;

  private final long key;
  private final String type;
  private final JobHeaders headers;
  private final Map<String, Object> customHeaders;
  private final String worker;
  private final int retries;
  private final Instant deadline;
  private final String variables;

  public ActivatedJobImpl(ZeebeObjectMapper objectMapper, GatewayOuterClass.ActivatedJob job) {
    this.objectMapper = objectMapper;

    key = job.getKey();
    type = job.getType();
    headers = new JobHeadersImpl(job.getJobHeaders());
    customHeaders = objectMapper.fromJsonAsMap(job.getCustomHeaders());
    worker = job.getWorker();
    retries = job.getRetries();
    deadline = Instant.ofEpochMilli(job.getDeadline());
    variables = job.getVariables();
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
  public JobHeaders getHeaders() {
    return headers;
  }

  @Override
  public Map<String, Object> getCustomHeaders() {
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
  public Instant getDeadline() {
    return deadline;
  }

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    return objectMapper.fromJsonAsMap(variables);
  }

  @Override
  public <T> T getVariablesAsType(Class<T> variableType) {
    return objectMapper.fromJson(variables, variableType);
  }

  @Override
  public String toJson() {
    return objectMapper.toJson(this);
  }
}
