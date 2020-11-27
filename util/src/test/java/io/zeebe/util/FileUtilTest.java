/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileUtilTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void shouldDeleteFolder() throws IOException {
    final File root = tempFolder.getRoot();

    tempFolder.newFile("file1");
    tempFolder.newFile("file2");
    tempFolder.newFolder("testFolder");

    FileUtil.deleteFolder(root.getAbsolutePath());

    assertThat(root.exists()).isFalse();
  }

  @Test
  public void shouldThrowExceptionForNonExistingFolder() {
    final File root = tempFolder.getRoot();

    tempFolder.delete();

    assertThatThrownBy(
            () -> {
              FileUtil.deleteFolder(root.getAbsolutePath());
            })
        .isInstanceOf(NoSuchFileException.class);
  }
}
