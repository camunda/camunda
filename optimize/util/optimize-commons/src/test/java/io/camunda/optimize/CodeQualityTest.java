/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CodeQualityTest {

  @Test
  public void testNoLombokUsed() {
    File thisFilePath = null;
    try {
      thisFilePath =
          new File(
              CodeQualityTest.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI()
                  .getPath());
    } catch (final URISyntaxException e) {
      throw new OptimizeRuntimeException(e);
    }

    final File optimizeDirectory =
        thisFilePath // test-classes
            .getParentFile() // target
            .getParentFile() // optimize-commons
            .getParentFile() // util
            .getParentFile(); // optimize

    checkForForbiddenAnnotations(optimizeDirectory);
  }

  private static void checkForForbiddenAnnotations(final File currentDirectory) {
    final String[] forbiddenStrings = {
      "@NoArgsConstructor",
      "@AllArgsConstructor",
      "@RequiredArgsConstructor",
      "@SneakyThrows",
      "@NonNull",
      "@EqualsAndHashCode",
      "@ToString",
      "@Builder",
      "@Builder.Default",
      "@FieldNameConstants",
      "@UtilityClass",
      "@Data",
      "@Getter",
      "@Setter",
      "@Slf4j",
      "import lombok"
    };

    if (currentDirectory == null || !currentDirectory.isDirectory()) {
      return;
    }

    final String currentDirectoryName = currentDirectory.getName();
    if (".".equals(currentDirectoryName) || "..".equals(currentDirectoryName)) {
      return;
    }

    final File[] entries = currentDirectory.listFiles();
    for (final File entry : entries) {
      if (entry.isDirectory()) {
        checkForForbiddenAnnotations(entry);
      } else if (!entry.getName().endsWith("CodeQualityTest.java")
          && entry.getName().endsWith(".java")) {
        String fileContent = null;
        try {
          fileContent = new String(Files.readAllBytes(entry.toPath()));
        } catch (final IOException e) {
          throw new OptimizeRuntimeException(e);
        }

        for (final String forbiddenString : forbiddenStrings) {
          if (fileContent.contains(forbiddenString)) {
            Assertions.fail(
                "file "
                    + entry.getAbsolutePath()
                    + " contains the forbidden string '"
                    + forbiddenString
                    + "'");
          }
        }
      }
    }
  }
}
