/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackType;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class AdHocSubProcessOutputCollectionBehavior {
  private final MsgPackReader outputCollectionReader = new MsgPackReader();
  private final MsgPackWriter outputCollectionWriter = new MsgPackWriter();
  private final ExpandableArrayBuffer outputCollectionBuffer = new ExpandableArrayBuffer();
  private final DirectBuffer updatedOutputCollectionBuffer = new UnsafeBuffer(0, 0);

  public Either<Failure, DirectBuffer> appendToOutputCollection(
      final DirectBuffer outputCollection, final DirectBuffer newValue) {

    // read output collection
    outputCollectionReader.wrap(outputCollection, 0, outputCollection.capacity());
    final var token = outputCollectionReader.readToken();
    if (token.getType() != MsgPackType.ARRAY) {
      return Either.left(
          new Failure(
              "The output collection has the wrong type. Expected %s but was %s."
                  .formatted(MsgPackType.ARRAY, token.getType()),
              ErrorType.EXTRACT_VALUE_ERROR));
    }
    final int currentSize = token.getSize();
    final int valuesOffset = outputCollectionReader.getOffset();

    // write updated output collection
    outputCollectionWriter.wrap(outputCollectionBuffer, 0);
    outputCollectionWriter.writeArrayHeader(currentSize + 1);
    // add current values
    outputCollectionWriter.writeRaw(
        outputCollection, valuesOffset, outputCollection.capacity() - valuesOffset);
    // add new value
    outputCollectionWriter.writeRaw(newValue);

    final var length = outputCollectionWriter.getOffset();
    updatedOutputCollectionBuffer.wrap(outputCollectionBuffer, 0, length);

    return Either.right(updatedOutputCollectionBuffer);
  }
}
