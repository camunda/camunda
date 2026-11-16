/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.multiinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.BinaryValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.MultiInstanceRecordValue;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MultiInstanceRecord extends MsgpackBufferValue<MultiInstanceRecord>
    implements MsgpackRecordValue, MultiInstanceRecordValue {

  final List<BinaryValue> inputCollection = new ArrayList<>();
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @JsonIgnore
  public List<DirectBuffer> getInputCollectionBuffers() {
    return inputCollection.stream().map(BinaryValue::getValue).collect(Collectors.toList());
  }

  @Override
  public List<String> getInputCollection() {
    return getInputCollectionBuffers().stream().map(MsgPackConverter::convertToJson).toList();
  }

  public MultiInstanceRecord setInputCollection(final List<DirectBuffer> inputCollection) {
    inputCollection.forEach(
        inputElement -> {
          final var element = new BinaryValue();
          element.wrap(inputElement, 0, inputElement.capacity());
          this.inputCollection.add(element);
        });
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MultiInstanceRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<MultiInstanceRecord> codec() {
    return MultiInstanceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MultiInstanceRecord> ops() {
    return MultiInstanceRecordOps.INSTANCE;
  }
}
