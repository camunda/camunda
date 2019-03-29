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

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.msgpack.value.StringValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;

public class JobBatchRecord extends UnpackedObject {

  private final StringProperty typeProp = new StringProperty("type");
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final LongProperty timeoutProp = new LongProperty("timeout", Protocol.INSTANT_NULL_VALUE);
  private final IntegerProperty maxJobsToActivateProp = new IntegerProperty("maxJobsToActivate", 1);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>("jobKeys", new LongValue());
  private final ArrayProperty<JobRecord> jobsProp = new ArrayProperty<>("jobs", new JobRecord());
  private final ArrayProperty<StringValue> variablesProp =
      new ArrayProperty<>("variables", new StringValue());
  private final BooleanProperty truncatedProp = new BooleanProperty("truncated", false);

  public JobBatchRecord() {
    this.declareProperty(typeProp)
        .declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(maxJobsToActivateProp)
        .declareProperty(jobKeysProp)
        .declareProperty(jobsProp)
        .declareProperty(variablesProp)
        .declareProperty(truncatedProp);
  }

  public DirectBuffer getType() {
    return typeProp.getValue();
  }

  public JobBatchRecord setType(String type) {
    this.typeProp.setValue(type);
    return this;
  }

  public JobBatchRecord setType(DirectBuffer buf) {
    this.typeProp.setValue(buf);
    return this;
  }

  public JobBatchRecord setType(DirectBuffer buf, int offset, int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public DirectBuffer getWorker() {
    return workerProp.getValue();
  }

  public JobBatchRecord setWorker(String worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecord setWorker(DirectBuffer worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public JobBatchRecord setWorker(DirectBuffer worker, int offset, int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public long getTimeout() {
    return timeoutProp.getValue();
  }

  public JobBatchRecord setTimeout(long val) {
    timeoutProp.setValue(val);
    return this;
  }

  public int getMaxJobsToActivate() {
    return maxJobsToActivateProp.getValue();
  }

  public JobBatchRecord setMaxJobsToActivate(int maxJobsToActivate) {
    maxJobsToActivateProp.setValue(maxJobsToActivate);
    return this;
  }

  public ValueArray<LongValue> jobKeys() {
    return jobKeysProp;
  }

  public ValueArray<JobRecord> jobs() {
    return jobsProp;
  }

  public ValueArray<StringValue> variables() {
    return variablesProp;
  }

  public JobBatchRecord setTruncated(boolean truncated) {
    truncatedProp.setValue(truncated);
    return this;
  }

  public boolean getTruncated() {
    return truncatedProp.getValue();
  }
}
