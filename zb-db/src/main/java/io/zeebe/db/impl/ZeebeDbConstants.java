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
package io.zeebe.db.impl;

import java.nio.ByteOrder;

public class ZeebeDbConstants {

  /**
   * The byte order is used to write primitive data types into rocks db key or value buffers.
   *
   * <p>Be aware that {@link ByteOrder.LITTLE_ENDIAN} will reverse the ordering. If the keys start
   * with an long, like an timestamp, and the implementation depends on the correct ordering, then
   * this could be a problem.
   *
   * <p>Example: Say we have `1` and `256` as keys (type short), in little endian this means 1 =
   * 0000 0001 0000 0000 and 256 = 0000 0000 0000 0001. This means that 256 will be sorted before 1
   * in Rocks DB, because the first byte is smaller.
   *
   * <p>We use {@link ByteOrder.BIG_ENDIAN} for the ascending ordering.
   */
  public static final ByteOrder ZB_DB_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
}
