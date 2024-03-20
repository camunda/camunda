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
package io.camunda.common.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Java8Utils {

  private Java8Utils() {}

  public static byte[] readAllBytes(final InputStream inputStream) throws IOException {
    final int bufLen = 4 * 0x400; // 4KB
    final byte[] buf = new byte[bufLen];
    int readLen;
    IOException exception = null;

    try {
      try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        while ((readLen = inputStream.read(buf, 0, bufLen)) != -1) {
          outputStream.write(buf, 0, readLen);
        }
        return outputStream.toByteArray();
      }
    } catch (final IOException e) {
      exception = e;
      throw e;
    } finally {
      if (exception == null) {
        inputStream.close();
      } else {
        try {
          inputStream.close();
        } catch (final IOException e) {
          exception.addSuppressed(e);
        }
      }
    }
  }
}
