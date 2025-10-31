/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import org.agrona.DirectBuffer;

public final class AsciiTrieEnumParser<E extends Enum<E>> implements EnumParser<E> {
  private static final int PRINTABLE_ASCII_CHARS = 63;
  private final AsciiTrieNode root;
  private final Class<E> enumClass;

  public AsciiTrieEnumParser(final Class<E> enumClass) {
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

  @Override
  @SuppressWarnings("unchecked")
  public E parse(final DirectBuffer buffer, final int offset, final int length) {
    return (E) root.search(buffer, offset, length);
  }

  /** Map alphanumeric + '_' ASCII char to the index of the array */
  private static int charToIndex(final char c) {
    if (c >= 'A' && c <= 'Z') {
      return c - 'A';
    }
    if (c >= 'a' && c <= 'z') {
      return c - 'a' + 26;
    }
    if (c >= '0' && c <= '9') {
      return c - '0' + 52;
    }
    if (c == '_') {
      return 62;
    }
    return -1; // Invalid character
  }

  private static final class AsciiTrieNode {
    private AsciiTrieNode[] children; // printable alphanumeric chars ASCII characters 0-63
    private Enum<?> enumValue;

    public AsciiTrieNode() {
      //      children = new AsciiTrieNode[PRINTABLE_ASCII_CHARS];
      enumValue = null;
    }

    public void put(final String key, final Enum<?> value) {
      AsciiTrieNode current = this;
      for (int i = 0; i < key.length(); i++) {
        final char ch = key.charAt(i);
        final var index = charToIndex(ch);
        if (index == -1) {
          throw new IllegalArgumentException("Non-ASCII character: " + ch);
        }

        if (current.children == null) {
          current.children = new AsciiTrieNode[PRINTABLE_ASCII_CHARS];
        }
        if (current.children[index] == null) {
          current.children[index] = new AsciiTrieNode();
        }
        current = current.children[index];
      }
      current.enumValue = value;
    }

    public Enum<?> search(final DirectBuffer buffer, final int offset, final int length) {
      AsciiTrieNode current = this;

      for (int i = 0; i < length; i++) {
        final byte b = buffer.getByte(offset + i);
        final var index = charToIndex((char) b);
        if (index == -1) {
          return null; // invalid character
        }

        final AsciiTrieNode next = current.children[index];
        if (next == null) {
          return null; // No match found
        }
        current = next;
      }

      return current.enumValue;
    }
  }
}
