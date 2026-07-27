/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import com.google.common.base.Strings;

public sealed interface TargetIndex permits MainIndex, OrdinalIndex {
  String name();

  static TargetIndex mainIndex(final String name) {
    return new MainIndex(name);
  }

  static TargetIndex ordinalIndex(final String prefix, final int ordinal) {
    final var suffix = "ord" + Strings.padStart(String.valueOf(ordinal), 5, '0');
    return new OrdinalIndex(suffix, ordinal, prefix + suffix);
  }
}
