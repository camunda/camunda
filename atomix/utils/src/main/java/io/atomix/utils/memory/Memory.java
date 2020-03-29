/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.memory;

/**
 * Memory allocator.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface Memory {

  /**
   * Returns the memory count.
   *
   * @return The memory count.
   */
  int size();

  /** Frees the memory. */
  void free();

  /** Memory utilities. */
  class Util {

    /** Returns a boolean indicating whether the given count is a power of 2. */
    public static boolean isPow2(final int size) {
      return size > 0 & (size & (size - 1)) == 0;
    }

    /** Rounds the count to the nearest power of two. */
    public static long toPow2(final int size) {
      if ((size & (size - 1)) == 0) {
        return size;
      }
      int i = 128;
      while (i < size) {
        i *= 2;
        if (i <= 0) {
          return 1L << 62;
        }
      }
      return i;
    }
  }
}
