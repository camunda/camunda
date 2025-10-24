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
package io.camunda.zeebe.client.impl.command;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class StreamUtil {

  public static byte[] readInputStream(final InputStream input) throws IOException {
    final byte[] byteBuffer = new byte[4 * 1024];
    int readBytes;

    try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
      while ((readBytes = input.read(byteBuffer, 0, byteBuffer.length)) != -1) {
        buffer.write(byteBuffer, 0, readBytes);
      }

      buffer.flush();

      return buffer.toByteArray();
    }
  }
}
