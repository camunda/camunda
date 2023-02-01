/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.zeebe;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@EqualsAndHashCode
@Getter
@Setter
@FieldNameConstants
@NoArgsConstructor
public abstract class ZeebeRecordDto<VALUE extends RecordValue, INTENT extends Intent> implements Record<VALUE> {

  private long position;
  private Long sequence; // this field was introduced with 8.2.0, it will not be present in records of prior versions
  private long sourceRecordPosition;
  private long key;
  private long timestamp;
  private int partitionId;
  private RecordType recordType;
  private RejectionType rejectionType;
  private String rejectionReason;
  private String brokerVersion;
  private ValueType valueType;
  private VALUE value;
  private INTENT intent;

  @Override
  public Record<VALUE> clone() {
    throw new UnsupportedOperationException("Operation not supported");
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }

}
