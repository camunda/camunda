/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.NamedFileSet;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.FileSet;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSetManagerTest {

  @TempDir Path backupDir;

  @TempDir Path tempDir;
  private FileSetManager fileSetManager;
  private BackupIdentifier backupIdentifier;
  private FileSet fileSet;

  @BeforeEach
  void setUp() {

    // do not mock the backupIdentifier
    fileSetManager = new FileSetManager(backupDir.toString());
    backupIdentifier = new BackupIdentifierImpl(1337, 0, 42L);
    fileSet = mock(FileSet.class);
  }

  @Test
  void testSave() throws IOException {
    final Path filePath = tempDir.resolve("testFile.txt");
    Files.createFile(filePath);
    final var namedFileSet = new NamedFileSetImpl(Map.of("testFile.txt", filePath));

    fileSetManager.save(backupIdentifier, "fileSetName", namedFileSet);

    final Path savedFilePath = backupDir.resolve("contents/0/42/1337/fileSetName/testFile.txt");
    assertThat(Files.exists(savedFilePath)).isTrue();
  }

  @Test
  void testDelete() throws IOException {
    final Path fileSetPath = backupDir.resolve("contents/0/42/1337/fileSetName");
    Files.createDirectories(fileSetPath);

    fileSetManager.delete(backupIdentifier, "fileSetName");

    assertThat(Files.exists(fileSetPath)).isFalse();
  }

  @Test
  void testDeleteNonExistingFile() {
    fileSetManager.delete(backupIdentifier, "nonExistingFileSet");
  }

  @Test
  void testRestore() throws IOException {
    final Path backupFilePath = backupDir.resolve("contents/0/42/1337/fileSetName/testFile.txt");
    Files.createDirectories(backupFilePath.getParent());
    Files.createFile(backupFilePath);
    when(fileSet.files()).thenReturn(List.of(new FileSet.NamedFile("testFile.txt")));

    final Path targetFolder = tempDir.resolve("restoreTarget");
    Files.createDirectories(targetFolder);

    final NamedFileSet restoredFileSet =
        fileSetManager.restore(backupIdentifier, "fileSetName", fileSet, targetFolder);

    final Path restoredFilePath = targetFolder.resolve("testFile.txt");
    assertThat(Files.exists(restoredFilePath)).isTrue();
    assertThat(restoredFileSet.namedFiles().get("testFile.txt")).isEqualTo(restoredFilePath);
  }
}
