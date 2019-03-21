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
package io.zeebe.broker.exporter.record;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;

@JsonAutoDetect(fieldVisibility = Visibility.NONE, getterVisibility = Visibility.PUBLIC_ONLY)
@JsonInclude(Include.NON_NULL)
public class RecordMetadataImpl implements RecordMetadata {
  private final ExporterObjectMapper objectMapper;
  private final ValueType valueType;
  private final Intent intent;
  private final RecordType recordType;
  private final int partitionId;
  private final RejectionType rejectionType;
  private final String rejectionReason;

  public RecordMetadataImpl(
      ExporterObjectMapper objectMapper,
      int partitionId,
      Intent intent,
      RecordType recordType,
      RejectionType rejectionType,
      String rejectionReason,
      ValueType valueType) {
    this.objectMapper = objectMapper;
    this.partitionId = partitionId;
    this.intent = intent;
    this.recordType = recordType;
    this.rejectionType = rejectionType;
    this.rejectionReason = rejectionReason;
    this.valueType = valueType;
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

  @Override
  public String toJson() {
    return objectMapper.toJson(this);
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
