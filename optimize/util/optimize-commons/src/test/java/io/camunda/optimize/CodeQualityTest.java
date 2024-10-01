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
  public void testNoForbiddenAnnotationUsed() {
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
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException(e);
    }

    File optimizeDirectory =
        thisFilePath // test-classes
            .getParentFile() // target
            .getParentFile() // optimize-commons
            .getParentFile() // util
            .getParentFile(); // optimize

    checkForForbiddenAnnotations(optimizeDirectory);
  }

  private static void checkForForbiddenAnnotations(File currentDirectory) {
    final String[] forbiddenAnnotations = {
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
      "@Data",
      "@Getter",
      "@Setter",
      "@Slf4j"
    };

    if (currentDirectory == null || !currentDirectory.isDirectory()) {
      return;
    }

    String currentDirectoryName = currentDirectory.getName();
    if (currentDirectoryName.equals(".") || currentDirectoryName.equals("..")) {
      return;
    }

    File[] entries = currentDirectory.listFiles();
    for (File entry : entries) {
      if (entry.isDirectory()) {
        checkForForbiddenAnnotations(entry);
      } else if (!entry.getName().endsWith("CodeQualityTest.java")
          && entry.getName().endsWith(".java")) {
        String fileContent = null;
        try {
          fileContent = new String(Files.readAllBytes(entry.toPath()));
        } catch (IOException e) {
          throw new OptimizeRuntimeException(e);
        }

        for (String forbiddenAnnotation : forbiddenAnnotations) {
          if (fileContent.contains(forbiddenAnnotation)) {
            Assertions.fail(
                "file "
                    + entry.getAbsolutePath()
                    + " contains the Lombok annotation "
                    + forbiddenAnnotation);
          }
        }
      }
    }
  }
}
