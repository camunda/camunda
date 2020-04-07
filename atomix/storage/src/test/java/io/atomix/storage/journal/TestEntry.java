/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.storage.journal;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.ArraySizeHashPrinter;
import java.util.Arrays;

/**
 * Test entry.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class TestEntry {
  private final byte[] bytes;

  public TestEntry(final int size) {
    this(new byte[size]);
  }

  public TestEntry(final byte[] bytes) {
    this.bytes = bytes;
  }

  public byte[] bytes() {
    return bytes;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TestEntry testEntry = (TestEntry) o;
    return Arrays.equals(bytes, testEntry.bytes);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("bytes", ArraySizeHashPrinter.of(bytes)).toString();
  }
}
