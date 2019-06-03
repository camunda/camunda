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
package io.zeebe.protocol.impl.record.value.job;

import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.msgpack.property.PackedProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.WorkflowInstanceRelated;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Instant;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, JobRecordValue {
  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public static final String RETRIES = "retries";
  public static final String TYPE = "type";
  public static final String CUSTOM_HEADERS = "customHeaders";
  public static final String VARIABLES = "variables";
  public static final String ERROR_MESSAGE = "errorMessage";

  private final LongProperty deadlineProp =
      new LongProperty("deadline", Protocol.INSTANT_NULL_VALUE);
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final IntegerProperty retriesProp = new IntegerProperty(RETRIES, -1);
  private final StringProperty typeProp = new StringProperty(TYPE, "");
  private final ObjectProperty<JobHeaders> headersProp =
      new ObjectProperty<>("headers", new JobHeaders());
  private final PackedProperty customHeadersProp = new PackedProperty(CUSTOM_HEADERS, NO_HEADERS);
  private final DocumentProperty variableProp = new DocumentProperty(VARIABLES);
  private final StringProperty errorMessageProp = new StringProperty(ERROR_MESSAGE, "");

  public JobRecord() {
    this.declareProperty(deadlineProp)
        .declareProperty(workerProp)
        .declareProperty(retriesProp)
        .declareProperty(typeProp)
        .declareProperty(headersProp)
        .declareProperty(customHeadersProp)
        .declareProperty(variableProp)
        .declareProperty(errorMessageProp);
  }

  public long getDeadlineLong() {
    return deadlineProp.getValue();
  }

  public JobRecord setDeadline(long val) {
    deadlineProp.setValue(val);
    return this;
  }

  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
  }

  public JobRecord setWorker(String worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public JobRecord setWorker(DirectBuffer worker) {
    return setWorker(worker, 0, worker.capacity());
  }

  public JobRecord setWorker(DirectBuffer worker, int offset, int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public int getRetries() {
    return retriesProp.getValue();
  }

  public JobRecord setRetries(int retries) {
    retriesProp.setValue(retries);
    return this;
  }

  public DirectBuffer getTypeBuffer() {
    return typeProp.getValue();
  }

  public JobRecord setType(String type) {
    this.typeProp.setValue(type);
    return this;
  }

  public JobRecord setType(DirectBuffer buf) {
    return setType(buf, 0, buf.capacity());
  }

  public JobRecord setType(DirectBuffer buf, int offset, int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public DirectBuffer getVariablesBuffer() {
    return variableProp.getValue();
  }

  public JobRecord setVariables(DirectBuffer variables) {
    variableProp.setValue(variables);
    return this;
  }

  public JobRecord resetVariables() {
    variableProp.reset();
    return this;
  }

  public JobHeaders getJobHeaders() {
    return headersProp.getValue();
  }

  public void setCustomHeaders(DirectBuffer buffer, int offset, int length) {
    customHeadersProp.setValue(buffer, offset, length);
  }

  public JobRecord setCustomHeaders(DirectBuffer buffer) {
    customHeadersProp.setValue(buffer, 0, buffer.capacity());
    return this;
  }

  public DirectBuffer getCustomHeadersBuffer() {
    return customHeadersProp.getValue();
  }

  public DirectBuffer getErrorMessageBuffer() {
    return errorMessageProp.getValue();
  }

  public JobRecord setErrorMessage(String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  public JobRecord setErrorMessage(DirectBuffer buf) {
    return setErrorMessage(buf, 0, buf.capacity());
  }

  public JobRecord setErrorMessage(DirectBuffer buf, int offset, int length) {
    errorMessageProp.setValue(buf, offset, length);
    return this;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return headersProp.getValue().getWorkflowInstanceKey();
  }

  @Override
  public Headers getHeaders() {
    return headersProp.getValue();
  }

  @Override
  public Map<String, Object> getCustomHeaders() {
    return MsgPackConverter.convertToMap(customHeadersProp.getValue());
  }

  @Override
  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessageProp.getValue());
  }

  @Override
  public String getType() {
    return BufferUtil.bufferAsString(typeProp.getValue());
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  @Override
  public Instant getDeadline() {
    return Instant.ofEpochMilli(deadlineProp.getValue());
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public String getVariables() {
    return MsgPackConverter.convertToJson(variableProp.getValue());
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("not yet implemented");
  }
}
