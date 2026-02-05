/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.records;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.RecordWithSerializedSize;
import io.camunda.zeebe.stream.util.Records;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

class TypedRecordImplTest {

  @Test
  void shouldImplementRecordWithSerializedSize() {
    // given
    final var typedRecord = new TypedRecordImpl(1);

    // then - TypedRecordImpl should implement RecordWithSerializedSize
    assertThat(typedRecord).isInstanceOf(RecordWithSerializedSize.class);
  }

  @Test
  void shouldReturnSerializedSize() {
    // given
    final var metadata = new RecordMetadata()
        .recordType(RecordType.EVENT)
        .intent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .valueType(ValueType.PROCESS_INSTANCE);
    
    final var processInstanceRecord = Records.processInstance(1);
    final var buffer = new ExpandableArrayBuffer();
    
    // Write metadata to calculate its length
    metadata.write(buffer, 0);
    final int metadataLength = metadata.getLength();
    final int valueLength = processInstanceRecord.getLength();
    final int expectedSize = metadataLength + valueLength;
    
    final var loggedEvent = new LoggedEvent() {
      @Override
      public long getKey() {
        return 1L;
      }

      @Override
      public long getPosition() {
        return 100L;
      }

      @Override
      public long getSourceEventPosition() {
        return -1L;
      }

      @Override
      public long getTimestamp() {
        return System.currentTimeMillis();
      }
    };
    
    final var typedRecord = new TypedRecordImpl(1);
    typedRecord.wrap(loggedEvent, metadata, processInstanceRecord);
    
    // when
    final int serializedSize = ((RecordWithSerializedSize) typedRecord).getSerializedSize();
    
    // then - serializedSize should equal the sum of metadata and value lengths
    assertThat(serializedSize).isEqualTo(expectedSize);
    assertThat(serializedSize).isEqualTo(typedRecord.getLength());
  }
}
