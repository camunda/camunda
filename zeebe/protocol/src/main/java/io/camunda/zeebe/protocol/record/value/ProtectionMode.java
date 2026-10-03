/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import java.util.Set;

/**
 * The kinds of data protection that can be declared for an exported value (product-hub #3805 and
 * siblings).
 *
 * <p>Composition rule: {@link #REDACT} is terminal and exclusive of the others -- once the exported
 * value is a row of asterisks there is nothing left to mask or encrypt. {@link #MASK} and {@link
 * #ENCRYPT} defend against different actors and compose freely. Valid combinations: {@code {}},
 * {@code {REDACT}}, {@code {MASK}}, {@code {ENCRYPT}}, {@code {MASK, ENCRYPT}}.
 */
public enum ProtectionMode {
  REDACT,
  MASK,
  ENCRYPT;

  /**
   * Validates the composition rule above.
   *
   * @throws IllegalArgumentException if {@code modes} contains {@link #REDACT} together with any
   *     other mode.
   */
  public static void validateCombination(final Set<ProtectionMode> modes) {
    if (modes.contains(REDACT) && modes.size() > 1) {
      throw new IllegalArgumentException(
          "REDACT cannot be combined with other protection modes, but got: " + modes);
    }
  }
}
