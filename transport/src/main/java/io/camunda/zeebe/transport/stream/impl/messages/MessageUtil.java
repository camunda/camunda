/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import io.camunda.zeebe.util.buffer.BufferReader;
import org.agrona.concurrent.UnsafeBuffer;

public final class MessageUtil {

  private MessageUtil() {
    // avoid instantiation of util class
  }

  public static PushStreamRequest parsePushRequest(final byte[] bytes) {
    return parseRequest(bytes, new PushStreamRequest());
  }

  public static RemoveStreamRequest parseRemoveRequest(final byte[] bytes) {
    return parseRequest(bytes, new RemoveStreamRequest());
  }

  public static AddStreamRequest parseAddRequest(final byte[] bytes) {
    return parseRequest(bytes, new AddStreamRequest());
  }

  private static <R extends BufferReader> R parseRequest(final byte[] bytes, final R request) {
    final var buffer = new UnsafeBuffer(bytes);
    request.wrap(buffer, 0, buffer.capacity());

    return request;
  }
}
