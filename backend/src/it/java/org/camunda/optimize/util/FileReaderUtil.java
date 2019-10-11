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
  public String getFileContentWithReplacedNewlinesAsString(String pathString) {
    Path path = Paths.get(FileReaderUtil.class.getResource(pathString).toURI());
    byte[] content = Files.readAllBytes(path);
    // Remove \r to ensure all newlines are marked with \n so that tests also run on Windows, see OPT-2744
    return new String(content, StandardCharsets.UTF_8).replace("\r", "");
  }
}
