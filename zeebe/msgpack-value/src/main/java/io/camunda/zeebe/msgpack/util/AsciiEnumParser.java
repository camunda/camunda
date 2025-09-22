/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import org.agrona.DirectBuffer;

public final class AsciiEnumParser<E extends Enum<E>> {
  private final AsciiTrieNode root;
  private final Class<E> enumClass;

  public AsciiEnumParser(final Class<E> enumClass) {
    this.enumClass = enumClass;
    root = new AsciiTrieNode();
    buildTrie();
  }

  private void buildTrie() {
    final E[] enumConstants = enumClass.getEnumConstants();
    for (final E enumConstant : enumConstants) {
      root.put(enumConstant.name(), enumConstant);
    }
  }

  @SuppressWarnings("unchecked")
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    return (E) root.search(buffer, offset, length);
  }

  public E parse(
      final DirectBuffer buffer, final int offset, final int length, final E defaultValue) {
    final E result = parse(buffer, offset, length);
    return result != null ? result : defaultValue;
  }

  private static final class AsciiTrieNode {
    private final AsciiTrieNode[] children; // ASCII characters 0-127
    private Enum<?> enumValue;

    public AsciiTrieNode() {
      children = new AsciiTrieNode[128];
      enumValue = null;
    }

    public void put(final String key, final Enum<?> value) {
      AsciiTrieNode current = this;
      for (int i = 0; i < key.length(); i++) {
        final char ch = key.charAt(i);
        if (ch >= 128) {
          throw new IllegalArgumentException("Non-ASCII character: " + ch);
        }

        if (current.children[ch] == null) {
          current.children[ch] = new AsciiTrieNode();
        }
        current = current.children[ch];
      }
      current.enumValue = value;
    }

    public Enum<?> search(final DirectBuffer buffer, final int offset, final int length) {
      AsciiTrieNode current = this;

      for (int i = 0; i < length; i++) {
        final byte b = buffer.getByte(offset + i);
        if (b < 0 || b >= 128) {
          return null; // Non-ASCII character
        }

        final AsciiTrieNode next = current.children[b];
        if (next == null) {
          return null; // No match found
        }
        current = next;
      }

      return current.enumValue;
    }
  }
}
