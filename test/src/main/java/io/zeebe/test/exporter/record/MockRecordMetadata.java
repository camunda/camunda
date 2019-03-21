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
package io.zeebe.test.exporter.record;

import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import java.util.Objects;

public class MockRecordMetadata extends ExporterMappedObject implements RecordMetadata, Cloneable {

  private Intent intent = WorkflowInstanceCreationIntent.CREATE;
  private int partitionId = 0;
  private RecordType recordType = RecordType.COMMAND;
  private RejectionType rejectionType = RejectionType.NULL_VAL;
  private String rejectionReason = "";
  private ValueType valueType = ValueType.WORKFLOW_INSTANCE_CREATION;

  public MockRecordMetadata() {}

  public MockRecordMetadata(
      Intent intent,
      int partitionId,
      RecordType recordType,
      RejectionType rejectionType,
      String rejectionReason,
      ValueType valueType) {
    this.intent = intent;
    this.partitionId = partitionId;
    this.recordType = recordType;
    this.rejectionType = rejectionType;
    this.rejectionReason = rejectionReason;
    this.valueType = valueType;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  public MockRecordMetadata setIntent(Intent intent) {
    this.intent = intent;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  public MockRecordMetadata setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public RecordType getRecordType() {
    return recordType;
  }

  public MockRecordMetadata setRecordType(RecordType recordType) {
    this.recordType = recordType;
    return this;
  }

  @Override
  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public MockRecordMetadata setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
    return this;
  }

  @Override
  public String getRejectionReason() {
    return rejectionReason;
  }

  public MockRecordMetadata setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
    return this;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  public MockRecordMetadata setValueType(ValueType valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MockRecordMetadata)) {
      return false;
    }

    final MockRecordMetadata metadata = (MockRecordMetadata) o;
    return getPartitionId() == metadata.getPartitionId()
        && Objects.equals(getIntent(), metadata.getIntent())
        && getRecordType() == metadata.getRecordType()
        && getRejectionType() == metadata.getRejectionType()
        && Objects.equals(getRejectionReason(), metadata.getRejectionReason())
        && getValueType() == metadata.getValueType();
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getIntent(),
        getPartitionId(),
        getRecordType(),
        getRejectionType(),
        getRejectionReason(),
        getValueType());
  }

  @Override
  public String toString() {
    return "MockRecordMetadata{"
        + "intent="
        + intent
        + ", partitionId="
        + partitionId
        + ", recordType="
        + recordType
        + ", rejectionType="
        + rejectionType
        + ", rejectionReason='"
        + rejectionReason
        + '\''
        + ", valueType="
        + valueType
        + '}';
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }
}
