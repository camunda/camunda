/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@UtilityClass
public class FileReaderUtil {

  @SneakyThrows
  public String readFile(String pathString) {
    Path path = Paths.get(FileReaderUtil.class.getResource(pathString).toURI());
    byte[] content = Files.readAllBytes(path);
    return new String(content, StandardCharsets.UTF_8);
  }

  public String readFileWithWindowsLineSeparator(String path) {
    return readFile(path).replace("\n", "\r\n");
  }

  public String readValidTestLicense() {
    return readFile("/license/ValidTestLicense.txt");
  }

  public String readValidTestLegacyLicense() {
    return readFile("/license/TestLegacyLicense_Valid.txt");
  }
}
