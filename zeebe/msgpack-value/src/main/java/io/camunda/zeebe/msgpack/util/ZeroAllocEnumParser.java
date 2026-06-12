/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Zero-allocation enum parser using a DirectBufferView as the map key.
 *
 * @param <E> the enum type
 */
public class ZeroAllocEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private final Map<DirectBufferView, E> lookup;
  // Thread-local view for parse() to avoid allocation
  private final ThreadLocal<DirectBufferView> threadView =
      ThreadLocal.withInitial(DirectBufferView::new);

  public ZeroAllocEnumParser(final Class<E> enumClass) {
    final int constantsCount = (int) Arrays.stream(enumClass.getEnumConstants()).count();
    lookup = constantsCount > 16 ? new HashMap<>() : new TreeMap<>();
    for (final E e : enumClass.getEnumConstants()) {
      final byte[] bytes = e.name().getBytes();
      final DirectBufferView key = new DirectBufferView();
      key.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
      lookup.put(key, e);
    }
  }

  @Override
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    final DirectBufferView view = threadView.get();
    view.wrap(buffer, offset, length);
    return lookup.get(view);
  }
}
