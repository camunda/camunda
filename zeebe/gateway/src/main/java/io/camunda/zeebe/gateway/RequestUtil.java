/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.agrona.LangUtil.rethrowUnchecked;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RequestUtil {

  public static DirectBuffer ensureJsonSet(final String value) {
    if (value == null || value.trim().isEmpty()) {
      return DocumentValue.EMPTY_DOCUMENT;
    } else {
      try {
        return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
      } catch (final RuntimeException e) {
        final var cause = e.getCause();
        if (cause instanceof final JsonParseException parseException) {

          final var descriptiveException =
              new JsonParseException(
                  parseException.getProcessor(),
                  "Invalid JSON value: " + value,
                  parseException.getLocation(),
                  cause);

          rethrowUnchecked(descriptiveException);
          return DocumentValue.EMPTY_DOCUMENT; // bogus return statement
        } else if (cause instanceof IllegalArgumentException) {
          rethrowUnchecked(cause);
          return DocumentValue.EMPTY_DOCUMENT;
        } else {
          throw e;
        }
      }
    }
  }
}
