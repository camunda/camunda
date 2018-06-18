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

import io.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;

public class FailingBufferWriter implements BufferWriter {
  @Override
  public int getLength() {
    return 10;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    throw new FailingBufferWriterException("Could not write - expected");
  }

  public static class FailingBufferWriterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FailingBufferWriterException(String string) {
      super(string);
    }
  }
}
