/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.agrona.DirectBuffer;

/**
 * Trie-based EnumParser implementation with minimal sorted children arrays. Only the required
 * children are stored in a sorted array for each node.
 *
 * @param <E> the enum type
 */
public class TrieEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private final TrieNode<E> root = new TrieNode<>();

  public TrieEnumParser(final Class<E> enumClass) {
    for (final E e : enumClass.getEnumConstants()) {
      final byte[] nameBytes = e.name().getBytes(StandardCharsets.US_ASCII);
      TrieNode<E> node = root;
      for (final byte b : nameBytes) {
        node = node.addChild(b);
      }
      node.value = e;
    }
  }

  @Override
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    TrieNode<E> node = root;
    for (int i = 0; i < length; i++) {
      final byte b = buffer.getByte(offset + i);
      node = node.getChild(b);
      if (node == null) {
        return null;
      }
    }
    return node.value;
  }

  @Override
  public E parse(
      final DirectBuffer buffer, final int offset, final int length, final E defaultValue) {
    final E value = parse(buffer, offset, length);
    return value != null ? value : defaultValue;
  }

  private static final class TrieNode<E> {
    private final ArrayList<Byte> keys = new ArrayList<>(); // sorted
    private final ArrayList<TrieNode<E>> children = new ArrayList<>();
    private E value;

    TrieNode<E> getChild(final byte b) {
      final int idx = java.util.Collections.binarySearch(keys, b);
      return idx >= 0 ? children.get(idx) : null;
    }

    TrieNode<E> addChild(final byte b) {
      final int idx = java.util.Collections.binarySearch(keys, b);
      if (idx >= 0) {
        return children.get(idx);
      }
      final int insertAt = -idx - 1;
      final TrieNode<E> child = new TrieNode<>();
      keys.add(insertAt, b);
      children.add(insertAt, child);
      return child;
    }
  }
}
