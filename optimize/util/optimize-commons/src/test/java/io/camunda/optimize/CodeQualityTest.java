/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import java.io.File;
import java.nio.file.Files;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class CodeQualityTest {
  @Test
  @SneakyThrows
  public void testNoForbiddenAnnotationUsed() {
    File thisFilePath =
        new File(
            CodeQualityTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .getPath());

    File optimizeDirectory =
        thisFilePath // test-classes
            .getParentFile() // target
            .getParentFile() // optimize-commons
            .getParentFile() // util
            .getParentFile(); // optimize

    checkForForbiddenAnnotations(optimizeDirectory);
  }

  private static String[] forbiddenAnnotations = {
    "@NoArgsConstructor", "@AllArgsConstructor", "@RequiredArgsConstructor", "@FieldNameConstants"
  };

  @SneakyThrows
  private static void checkForForbiddenAnnotations(File currentDirectory) {
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
        String fileContent = new String(Files.readAllBytes(entry.toPath()));
        for (String forbiddenAnnotation : forbiddenAnnotations) {
          if (fileContent.contains(forbiddenAnnotation)) {
            Assertions.fail(
                "file "
                    + entry.getAbsolutePath()
                    + " contains forbidden annotation "
                    + forbiddenAnnotation);
          }
        }
      }
    }
  }
}
