/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;

@UtilityClass
public class FileReaderUtil {

  @SneakyThrows
  public String readFile(final String pathString) {
    return IOUtils.toString(FileReaderUtil.class.getResource(pathString).toURI(), StandardCharsets.UTF_8);
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
