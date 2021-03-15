/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;

public final class TestFileUtil {

  /** Ant-style property substitution */
  public static InputStream readAsTextFileAndReplace(
      final InputStream inputStream, final Charset charset, final Map<String, String> properties) {
    final String fileContent;
    try (final BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, charset))) {
      final StringBuilder sb = new StringBuilder();

      reader
          .lines()
          .forEach(
              (line) -> {
                String replacingLine = line;

                for (final Map.Entry<String, String> replacement : properties.entrySet()) {
                  final String property = "\\$\\{" + replacement.getKey() + "\\}";
                  replacingLine =
                      replacingLine.replaceAll(
                          property, Matcher.quoteReplacement(replacement.getValue()));
                }

                sb.append(replacingLine);
                sb.append("\n");
              });

      fileContent = sb.toString();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    return new ByteArrayInputStream(fileContent.getBytes(charset));
  }
}
