/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

public sealed interface TargetIndex permits MainIndex, OrdinalIndex {
  String ORDINAL_SUFFIX_START = "ord";

  String name();

  /*
   * Returns the index family for this target index - for a main index this will be
   * the name of the index itself, but for ordinal indexes it will be the part of the name
   * prior to the ordinal part.
   */
  IndexFamily getIndexFamily();

  static IndexFamily getIndexFamilyFromName(final String name) {
    return OrdinalIndex.fromName(name).orElseGet(() -> new MainIndex(name)).getIndexFamily();
  }

  static TargetIndex mainIndex(final String name) {
    return new MainIndex(name);
  }

  static TargetIndex ordinalIndex(final String prefix, final int ordinal) {
    return OrdinalIndex.of(prefix, ordinal);
  }
}
