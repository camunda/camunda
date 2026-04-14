/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
