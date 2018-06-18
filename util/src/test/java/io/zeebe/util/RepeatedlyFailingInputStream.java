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
package io.zeebe.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Throws an {@link IOException} on every {@code failureFrequency} call to {@link #read}. Otherwise
 * reads the next byte from the {@code underlyingInputStream}.
 */
public class RepeatedlyFailingInputStream extends InputStream {

  public static final long DEFAULT_FAILURE_FREQUENCY = 8;

  private final InputStream underlyingInputStream;
  private final long failureFrequency;

  private long readCount;

  public RepeatedlyFailingInputStream(final InputStream underlyingInputStream) {
    this(underlyingInputStream, DEFAULT_FAILURE_FREQUENCY);
  }

  public RepeatedlyFailingInputStream(
      final InputStream underlyingInputStream, final long failureFrequency) {
    this.underlyingInputStream = underlyingInputStream;
    this.failureFrequency = failureFrequency;

    readCount = 0;
  }

  @Override
  public int read() throws IOException {
    readCount++;

    if (readCount % failureFrequency == 0) {
      throw new IOException("Read failure - try again");
    } else {
      return underlyingInputStream.read();
    }
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    readCount = 0;
    return super.read(b, off, len);
  }
}
