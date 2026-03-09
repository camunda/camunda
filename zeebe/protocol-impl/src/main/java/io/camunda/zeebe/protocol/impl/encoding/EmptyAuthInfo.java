/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * An immutable, singleton-safe empty {@link AuthInfo}. This subclass is frozen at construction and
 * caches all derived values (claims, decoded map, serialized buffer) eagerly since they never
 * change. By overriding {@link #write}, it avoids the shared {@code MsgPackWriter} in the parent
 * class, eliminating a data race when multiple threads call {@code write()} on the singleton.
 */
final class EmptyAuthInfo extends AuthInfo {

  private static final EmptyAuthInfo INSTANCE = new EmptyAuthInfo();

  private static final Map<String, Object> EMPTY_MAP = Map.of();
  private final DirectBuffer cachedBuffer;
  private final int length;

  private EmptyAuthInfo() {
    super();
    length = super.getLength();
    // Cannot use super.toDirectBuffer() because it calls write(buffer, 0) which dispatches
    // to our override, but cachedBuffer is still null. Use super.write() directly instead.
    final var bytes = new byte[length];
    final var buf = new UnsafeBuffer(bytes);
    super.write(buf, 0);
    cachedBuffer = buf;
    freeze();
  }

  static AuthInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public int getLength() {
    return length;
  }

  @Override
  public DirectBuffer toDirectBuffer() {
    return cachedBuffer;
  }

  @Override
  public Map<String, Object> toDecodedMap() {
    return EMPTY_MAP;
  }

  @Override
  public boolean hasAnyClaims() {
    return false;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putBytes(offset, cachedBuffer, 0, length);
    return length;
  }
}
