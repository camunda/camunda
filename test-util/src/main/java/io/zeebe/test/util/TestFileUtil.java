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
package io.zeebe.test.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;

public class TestFileUtil {

  /** Ant-style property substitution */
  public static InputStream readAsTextFileAndReplace(
      InputStream inputStream, Charset charset, Map<String, String> properties) {
    final String fileContent;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
      final StringBuilder sb = new StringBuilder();

      reader
          .lines()
          .forEach(
              (line) -> {
                String replacingLine = line;

                for (Map.Entry<String, String> replacement : properties.entrySet()) {
                  final String property = "\\$\\{" + replacement.getKey() + "\\}";
                  replacingLine =
                      replacingLine.replaceAll(
                          property, Matcher.quoteReplacement(replacement.getValue()));
                }

                sb.append(replacingLine);
                sb.append("\n");
              });

      fileContent = sb.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return new ByteArrayInputStream(fileContent.getBytes(charset));
  }
}
