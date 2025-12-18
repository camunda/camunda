/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.api.Backup;
import java.nio.file.Path;
import org.assertj.core.api.AbstractAssert;

public final class BackupAssert extends AbstractAssert<BackupAssert, Backup> {

  private BackupAssert(final Backup actual, final Class<?> selfType) {
    super(actual, selfType);
  }

  public static BackupAssert assertThatBackup(final Backup actual) {
    return new BackupAssert(actual, BackupAssert.class);
  }

  @SuppressWarnings("UnusedReturnValue")
  public BackupAssert hasSameContentsAs(final Backup expected) {
    assertThat(actual.id()).isEqualTo(expected.id());
    assertThat(actual.descriptor()).isEqualTo(expected.descriptor());
    assertThat(actual.snapshot().names()).isEqualTo(expected.snapshot().names());
    assertThat(actual.segments().names()).isEqualTo(expected.segments().names());

    NamedFileSetAssert.assertThatNamedFileSet(actual.snapshot())
        .hasSameContentsAs(expected.snapshot());
    NamedFileSetAssert.assertThatNamedFileSet(actual.segments())
        .hasSameContentsAs(expected.segments());

    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  public BackupAssert residesInPath(final Path expectedPath) {
    NamedFileSetAssert.assertThatNamedFileSet(actual.snapshot()).residesInPath(expectedPath);
    NamedFileSetAssert.assertThatNamedFileSet(actual.segments()).residesInPath(expectedPath);
    return this;
  }
}
