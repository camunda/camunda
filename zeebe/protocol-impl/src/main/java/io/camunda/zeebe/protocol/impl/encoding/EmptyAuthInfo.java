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

/**
 * An immutable, singleton-safe empty {@link AuthInfo}. This subclass guards against accidental
 * mutation of a shared static instance by throwing {@link UnsupportedOperationException} on {@link
 * #wrap} and {@link #reset}. All derived values (claims, decoded map, serialized buffer) are cached
 * eagerly since they never change.
 */
public final class EmptyAuthInfo extends AuthInfo {

  private static final EmptyAuthInfo INSTANCE = new EmptyAuthInfo();

  private static final Map<String, Object> EMPTY_MAP = Map.of();
  private final DirectBuffer cachedBuffer;

  private EmptyAuthInfo() {
    super();
    cachedBuffer = super.toDirectBuffer();
  }

  public static AuthInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public Map<String, Object> getClaims() {
    return EMPTY_MAP;
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException("EmptyAuthInfo is immutable");
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    throw new UnsupportedOperationException("EmptyAuthInfo is immutable");
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
}
