/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class PayloadReader {

  public static String readVariables(final String payloadPath) {
    try {
      final var classLoader = PayloadReader.class.getClassLoader();
      try (final InputStream fromResource = classLoader.getResourceAsStream(payloadPath)) {
        if (fromResource != null) {
          return tryReadVariables(fromResource);
        }
        // unable to find from resources, try as regular file
        try (final InputStream fromFile = new FileInputStream(payloadPath)) {
          return tryReadVariables(fromFile);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private static String tryReadVariables(final InputStream inputStream) throws IOException {
    final StringBuilder stringBuilder = new StringBuilder();
    try (final InputStreamReader reader = new InputStreamReader(inputStream)) {
      try (final BufferedReader br = new BufferedReader(reader)) {
        String line;
        while ((line = br.readLine()) != null) {
          stringBuilder.append(line).append("\n");
        }
      }
    }
    return stringBuilder.toString();
  }
}
