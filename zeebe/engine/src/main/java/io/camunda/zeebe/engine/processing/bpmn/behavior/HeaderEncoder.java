/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class HeaderEncoder {

  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final Logger logger;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

  public HeaderEncoder(final Logger logger) {
    this.logger = logger;
  }

  public DirectBuffer encode(final Map<String, String> taskHeaders) {
    if (taskHeaders == null || taskHeaders.isEmpty()) {
      return JobRecord.NO_HEADERS;
    }

    final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    final var validHeaders =
        taskHeaders.entrySet().stream()
            .filter(entry -> isValidHeader(entry.getKey(), entry.getValue()))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

    if (validHeaders.size() != taskHeaders.size()) {
      logger.debug("Ignored {} invalid headers.", taskHeaders.size() - validHeaders.size());
    }

    final ExpandableArrayBuffer expandableBuffer =
        new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * validHeaders.size());

    msgPackWriter.wrap(expandableBuffer, 0);
    msgPackWriter.writeMapHeader(validHeaders.size());

    validHeaders.forEach(
        (k, v) -> {
          final DirectBuffer key = wrapString(k);
          msgPackWriter.writeString(key);

          final DirectBuffer value = wrapString(v);
          msgPackWriter.writeString(value);
        });

    buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());

    return buffer;
  }

  private boolean isValidHeader(final String key, final String value) {
    return key != null && !key.isEmpty() && value != null && !value.isEmpty();
  }
}
