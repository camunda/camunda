/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.camunda.operate.zeebeimport.record;

import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;

public class RecordMetadataImpl implements RecordMetadata {
  private int partitionId;
  private Intent intent;
  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private ValueType valueType;

  public RecordMetadataImpl() {
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public RecordType getRecordType() {
    return recordType;
  }

  @Override
  public RejectionType getRejectionType() {
    return rejectionType;
  }

  @Override
  public String getRejectionReason() {
    return rejectionReason;
  }

  @Override
  public ValueType getValueType() {
    return valueType;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public void setIntent(Intent intent) {
    this.intent = intent;
  }

  public void setRecordType(RecordType recordType) {
    this.recordType = recordType;
  }

  public void setRejectionType(RejectionType rejectionType) {
    this.rejectionType = rejectionType;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public void setValueType(ValueType valueType) {
    this.valueType = valueType;
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

  @Override
  public String toString() {
    return "RecordMetadataImpl{"
        + "partitionId="
        + partitionId
        + ", intent="
        + intent
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
}
