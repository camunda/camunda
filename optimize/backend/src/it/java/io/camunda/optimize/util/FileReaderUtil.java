/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileReaderUtil {

  @SneakyThrows
  public static String readFile(final String pathString) {
    return IOUtils.toString(
        FileReaderUtil.class.getResource(pathString).toURI(), StandardCharsets.UTF_8);
  }

  public static String readFileWithWindowsLineSeparator(String path) {
    return readFile(path).replace("\n", "\r\n");
  }

  public static String readValidTestLicense() {
    return readFile("/license/ValidTestLicense.txt");
  }

  public static String readValidTestLegacyLicense() {
    return readFile("/license/TestLegacyLicense_Valid.txt");
  }
}
