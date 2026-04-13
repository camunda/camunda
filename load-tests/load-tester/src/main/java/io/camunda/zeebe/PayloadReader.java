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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PayloadReader {

  private final ResourceLoader resourceLoader;

  PayloadReader(final ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  public String readVariables(final String payloadPath) {
    try {
      // try classpath first
      final var classpathResource = resourceLoader.getResource("classpath:" + payloadPath);
      if (classpathResource.exists()) {
        try (final InputStream is = classpathResource.getInputStream()) {
          return tryReadVariables(is);
        }
      }
      // fall back to file system
      final var fileResource = resourceLoader.getResource("file:" + payloadPath);
      try (final InputStream is = fileResource.getInputStream()) {
        return tryReadVariables(is);
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
