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
package io.zeebe.client.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.client.api.record.JobRecord;
import io.zeebe.client.impl.data.PayloadField;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public abstract class JobRecordImpl extends RecordImpl implements JobRecord {
  private Map<String, Object> headers = new HashMap<>();
  private Map<String, Object> customHeaders = new HashMap<>();

  private Instant deadline;
  private String worker;
  private Integer retries;
  private String type;
  private PayloadField payload;

  public JobRecordImpl(ZeebeObjectMapperImpl objectMapper, RecordType recordType) {
    super(objectMapper, recordType, ValueType.JOB);
    this.payload = new PayloadField(objectMapper);
  }

  public JobRecordImpl(JobRecordImpl base, JobIntent intent) {
    super(base, intent);

    this.headers = new HashMap<>(base.headers);
    this.customHeaders = new HashMap<>(base.customHeaders);
    this.deadline = base.deadline;
    this.worker = base.worker;
    this.retries = base.retries;
    this.type = base.type;

    this.payload = new PayloadField(base.payload);
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public Instant getDeadline() {
    return deadline;
  }

  public void setDeadline(Instant deadline) {
    this.deadline = deadline;
  }

  @Override
  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, Object> headers) {
    this.headers.clear();
    this.headers.putAll(headers);
  }

  @Override
  public Map<String, Object> getCustomHeaders() {
    return customHeaders;
  }

  public void setCustomHeaders(Map<String, Object> customHeaders) {
    this.customHeaders.clear();
    this.customHeaders.putAll(customHeaders);
  }

  @Override
  public String getWorker() {
    return worker;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  @JsonProperty("payload")
  public PayloadField getPayloadField() {
    return payload;
  }

  @JsonProperty("payload")
  public void setPayloadField(PayloadField payload) {
    if (payload != null) {
      this.payload = payload;
    }
  }

  @Override
  public String getPayload() {
    return payload.getAsJsonString();
  }

  @JsonIgnore
  @Override
  public Map<String, Object> getPayloadAsMap() {
    return payload.getAsMap();
  }

  @JsonIgnore
  @Override
  public <T> T getPayloadAsType(Class<T> payloadType) {
    return payload.getAsType(payloadType);
  }

  public void setPayload(String jsonString) {
    this.payload.setJson(jsonString);
  }

  public void setPayload(InputStream jsonStream) {
    this.payload.setJson(jsonStream);
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload.setAsMap(payload);
  }

  public void setPayload(Object payload) {
    this.payload.setAsObject(payload);
  }

  @Override
  public Integer getRetries() {
    return retries;
  }

  public void setRetries(Integer retries) {
    this.retries = retries;
  }

  @Override
  public Class<? extends RecordImpl> getEventClass() {
    return JobEventImpl.class;
  }
}
