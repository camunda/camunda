/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

public final class SnapshotCopyUtil {

  /**
   * @return a {@link BiConsumer }that copies all files from the path at first argument to the path
   *     at second argument. Used to copy all files of a snapshot from one place to another to test
   *     the copy
   */
  public static void copyAllFiles(final Path source, final Path target) {
    try (final var stream = Files.walk(source)) {
      stream.forEach(
          path -> {
            if (!source.equals(path)) {
              try {
                final var relativePath = source.relativize(path);
                final var targetPath = target.resolve(relativePath);
                Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            }
          });
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
