/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.cluster.dynamicnodeid;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryValidator;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DataDirectoryValidatorAssertions implements DataDirectoryValidator {

  private final boolean assertHardLinked;
  private final AtomicInteger filesChecked = new AtomicInteger(0);

  public DataDirectoryValidatorAssertions(final boolean hardLinked) {
    assertHardLinked = hardLinked;
  }

  @Override
  public void validate(final Path source, final Path target, final String markerFileName)
      throws IOException {
    // First perform standard validation to ensure files exist
    assertFilesAreHardLinkedOrCopied(source, target, assertHardLinked);
  }

  private void assertFilesAreHardLinkedOrCopied(
      final Path v0Dir, final Path v1Dir, final boolean assertHardLinked) throws IOException {
    final String markerFile = "directory-initialized.json";

    Files.walkFileTree(
        v1Dir,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(
              final Path dir, final BasicFileAttributes attrs) {
            // Skip runtime directories
            if (dir.getFileName().toString().equals("runtime")) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path target, final BasicFileAttributes attrs)
              throws IOException {
            final Path relative = v1Dir.relativize(target);

            // Skip marker file
            if (relative.getFileName().toString().equals(markerFile)) {
              return FileVisitResult.CONTINUE;
            }

            final Path source = v0Dir.resolve(relative);

            // Source file should exist
            assertThat(source).exists();

            // Files should be hard-linked (same inode)
            if (assertHardLinked) {
              assertThat(areHardLinked(source, target))
                  .as("File %s should be hard-linked to %s", source, target)
                  .isTrue();
            } else {
              assertThat(target).hasSameBinaryContentAs(source);
            }

            filesChecked.incrementAndGet();
            return FileVisitResult.CONTINUE;
          }
        });
  }

  public void assertFilesHaveBeenvalidated() {
    // Ensure we actually checked some files
    assertThat(filesChecked.get()).as("Should have verified at least some files").isGreaterThan(0);
  }

  private boolean areHardLinked(final Path file1, final Path file2) throws IOException {
    final var key1 = Files.readAttributes(file1, BasicFileAttributes.class).fileKey();
    final var key2 = Files.readAttributes(file2, BasicFileAttributes.class).fileKey();
    return key1 != null && key2 != null && key1.equals(key2);
  }
}
