/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.springframework.stereotype.Component;

@Component
public class PayloadReader {

  public String readPayload(final String payloadPath) {
    try {
      final var classLoader = PayloadReader.class.getClassLoader();
      try (final InputStream fromResource = classLoader.getResourceAsStream(payloadPath)) {
        if (fromResource != null) {
          return readStream(fromResource);
        }
        try (final InputStream fromFile = new FileInputStream(payloadPath)) {
          return readStream(fromFile);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedExecutionException(e);
    }
  }

  private String readStream(final InputStream inputStream) throws IOException {
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
