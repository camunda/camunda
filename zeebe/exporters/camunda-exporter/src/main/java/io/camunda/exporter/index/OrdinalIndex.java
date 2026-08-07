/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.index;

import com.google.common.base.Strings;
import java.util.Optional;
import java.util.regex.Pattern;

record OrdinalIndex(IndexFamily indexFamily, int ordinal, String name) implements TargetIndex {
  static final String ORDINAL_SUFFIX = "ord";
  static final Pattern ORDINAL_INDEX_PATTERN =
      Pattern.compile("^(.*)" + Pattern.quote(ORDINAL_SUFFIX) + "(\\d{5})$");

  static Optional<TargetIndex> fromName(final String indexName) {
    final var matcher = ORDINAL_INDEX_PATTERN.matcher(indexName);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    final var prefix = matcher.group(1);
    final var ordinal = Integer.parseInt(matcher.group(2));
    return Optional.of(of(prefix, ordinal));
  }

  static OrdinalIndex of(final String prefix, final int ordinal) {
    final var name = prefix + ORDINAL_SUFFIX + Strings.padStart(String.valueOf(ordinal), 5, '0');
    return new OrdinalIndex(new IndexFamilyImpl(prefix), ordinal, name);
  }

  @Override
  public IndexFamily getIndexFamily() {
    return indexFamily;
  }
}
