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
import java.io.OutputStream;

/** */
public class RepeatedlyFailingOutputStream extends OutputStream {
  public static final long DEFAULT_FAILURE_FREQUENCY = 8;

  private final OutputStream underlyingOutputStream;
  private final long failureFrequency;

  private long writeCount;

  public RepeatedlyFailingOutputStream(final OutputStream underlyingOutputStream) {
    this(underlyingOutputStream, DEFAULT_FAILURE_FREQUENCY);
  }

  public RepeatedlyFailingOutputStream(
      final OutputStream underlyingOutputStream, final long failureFrequency) {
    this.underlyingOutputStream = underlyingOutputStream;
    this.failureFrequency = failureFrequency;

    writeCount = 0;
  }

  public OutputStream getUnderlyingOutputStream() {
    return underlyingOutputStream;
  }

  @Override
  public void write(int b) throws IOException {
    writeCount++;

    if (writeCount % failureFrequency == 0) {
      throw new IOException("Write failure");
    } else {
      underlyingOutputStream.write(b);
    }
  }
}
