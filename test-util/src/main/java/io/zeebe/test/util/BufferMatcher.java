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

import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.mockito.ArgumentMatcher;

public class BufferMatcher implements ArgumentMatcher<DirectBuffer> {
  protected byte[] expectedBytes;
  protected int position = 0;

  @Override
  public boolean matches(DirectBuffer argument) {
    if (argument == null) {
      return false;
    }

    final byte[] actualBytes = new byte[expectedBytes.length];

    // TODO: try-catch in case buffer has not expected size
    argument.getBytes(position, actualBytes, 0, actualBytes.length);

    return Arrays.equals(expectedBytes, actualBytes);
  }

  public static BufferMatcher hasBytes(byte[] bytes) {
    final BufferMatcher matcher = new BufferMatcher();

    matcher.expectedBytes = bytes;

    return matcher;
  }

  public BufferMatcher atPosition(int position) {
    this.position = position;
    return this;
  }
}
