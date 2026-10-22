/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.util.Comparator;

/**
 * The order in which a store lists keys: by the bytes of their UTF-8 encoding. That is the order of
 * their code points, where {@link String#compareTo} orders by UTF-16 units, and differs for
 * characters outside the basic multilingual plane.
 */
public final class KeyOrder {

  /** Orders keys as a store lists them. */
  public static final Comparator<String> COMPARATOR = KeyOrder::compare;

  private KeyOrder() {}

  /** Negative if {@code a} sorts before {@code b}, positive if after, and 0 if they are equal. */
  public static int compare(final String a, final String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      final int x = a.codePointAt(i);
      final int y = b.codePointAt(j);
      if (x != y) {
        return Integer.compare(x, y);
      }
      i += Character.charCount(x);
      j += Character.charCount(y);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }
}
