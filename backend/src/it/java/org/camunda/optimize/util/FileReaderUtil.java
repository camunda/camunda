/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileReaderUtil {

  @SneakyThrows
  public static String readFile(final String pathString) {
    return IOUtils.toString(FileReaderUtil.class.getResource(pathString).toURI(), StandardCharsets.UTF_8);
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
