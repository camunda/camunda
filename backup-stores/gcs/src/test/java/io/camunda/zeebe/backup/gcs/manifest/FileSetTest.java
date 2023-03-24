/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs.manifest;

import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.backup.gcs.manifest.FileSet.NamedFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class FileSetTest {
  @Test
  void shouldThrowOnDuplicatedFileNames() {
    // given
    final var files = List.of(new NamedFile("file1"), new NamedFile("file1"));

    // when then
    Assertions.assertThatThrownBy(() -> new FileSet(files))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected file name 'file1' to be unique, but occurred '2' times");
  }

  @Test
  void shouldConvertFromNamedFileSet() {
    // given
    final var namedFileSet =
        new NamedFileSetImpl(Map.of("file1", Path.of("path1"), "file2", Path.of("path2")));

    // when
    final var fileSet = FileSet.of(namedFileSet);

    // then
    Assertions.assertThat(fileSet.files())
        .containsExactlyInAnyOrder(new NamedFile("file1"), new NamedFile("file2"));
  }
}
