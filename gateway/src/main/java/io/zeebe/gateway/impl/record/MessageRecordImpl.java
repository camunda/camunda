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
package io.zeebe.gateway.impl.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.gateway.impl.data.PayloadField;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.gateway.impl.event.MessageEventImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public abstract class MessageRecordImpl extends RecordImpl {
  private String name;
  private String correlationKey;
  private long timeToLive;
  private String messageId;
  private PayloadField payload;

  public MessageRecordImpl(ZeebeObjectMapperImpl objectMapper, RecordType recordType) {
    super(objectMapper, recordType, ValueType.MESSAGE);
    this.payload = new PayloadField(objectMapper);
  }

  public MessageRecordImpl(MessageRecordImpl base, WorkflowInstanceIntent intent) {
    super(base, intent);

    this.name = base.name;
    this.correlationKey = base.correlationKey;
    this.messageId = base.messageId;

    this.payload = new PayloadField(base.payload);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(String correlationKey) {
    this.correlationKey = correlationKey;
  }

  @JsonProperty("timeToLive")
  public long getTimeToLiveInMillis() {
    return timeToLive;
  }

  @JsonProperty("timeToLive")
  public void setTimeToLiveInMillis(long timeToLive) {
    this.timeToLive = timeToLive;
  }

  public Duration getTimeToLive() {
    return Duration.ofMillis(timeToLive);
  }

  public void setTimeToLive(Duration timeToLive) {
    this.timeToLive = timeToLive.toMillis();
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String id) {
    this.messageId = id;
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

  public String getPayload() {
    return payload.getAsJsonString();
  }

  @JsonIgnore
  public Map<String, Object> getPayloadAsMap() {
    return payload.getAsMap();
  }

  @JsonIgnore
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
  public Class<? extends RecordImpl> getEventClass() {
    return MessageEventImpl.class;
  }
}
