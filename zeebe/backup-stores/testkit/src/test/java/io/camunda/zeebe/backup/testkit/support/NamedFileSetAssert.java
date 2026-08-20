/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.NamedFileSet;
import java.nio.file.Path;
import org.assertj.core.api.AbstractAssert;

final class NamedFileSetAssert extends AbstractAssert<NamedFileSetAssert, NamedFileSet> {

  private NamedFileSetAssert(final NamedFileSet namedFileSet, final Class<?> selfType) {
    super(namedFileSet, selfType);
  }

  public static NamedFileSetAssert assertThatNamedFileSet(final NamedFileSet actual) {
    return new NamedFileSetAssert(actual, NamedFileSetAssert.class);
  }

  @SuppressWarnings("UnusedReturnValue")
  public NamedFileSetAssert hasSameContentsAs(final NamedFileSet expected) {
    for (final var expectedEntry : expected.namedFiles().entrySet()) {
      final var expectedName = expectedEntry.getKey();
      final var expectedPath = expectedEntry.getValue();
      final var actualNamedFiles = actual.namedFiles();

      assertThat(actualNamedFiles).containsKey(expectedName);
      final var actualPath = actualNamedFiles.get(expectedEntry.getKey());
      assertThat(actualPath).hasSameBinaryContentAs(expectedPath);
    }
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public NamedFileSetAssert residesInPath(final Path expectedPath) {
    assertThat(actual.files())
        .allSatisfy(actualPath -> assertThat(actualPath).hasParent(expectedPath));
    return this;
  }
}
