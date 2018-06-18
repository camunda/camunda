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
package io.zeebe.test.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Only reads the first {@code bytesToRead} bytes from the underlying input stream. After that it
 * returns on every read {@link ShortReadInputStream#END_OF_STREAM} if {@code throwException} is
 * false, or an {@link IOException} otherwise.
 */
public class ShortReadInputStream extends InputStream {

  public static final int END_OF_STREAM = -1;

  private final InputStream underlyingInputStream;
  private final long bytesToRead;
  private final boolean throwException;

  private long readCount;

  public ShortReadInputStream(
      final InputStream underlyingInputStream,
      final long bytesToRead,
      final boolean throwException) {
    this.underlyingInputStream = underlyingInputStream;
    this.bytesToRead = bytesToRead;
    this.throwException = throwException;

    this.readCount = 0;
  }

  @Override
  public int read() throws IOException {
    readCount++;

    if (readCount > bytesToRead) {
      if (throwException) {
        throw new IOException("Read failure");
      } else {
        return END_OF_STREAM;
      }
    } else {
      return underlyingInputStream.read();
    }
  }
}
