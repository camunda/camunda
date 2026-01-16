/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.util;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public final class FileReaderUtil {

  private FileReaderUtil() {}

  public static String readFile(final String pathString) {
    try {
      return IOUtils.toString(
          FileReaderUtil.class.getResource(pathString).toURI(), StandardCharsets.UTF_8);
    } catch (final IOException | URISyntaxException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public static String readFileWithWindowsLineSeparator(final String path) {
    return readFile(path).replace("\n", "\r\n");
  }
}
