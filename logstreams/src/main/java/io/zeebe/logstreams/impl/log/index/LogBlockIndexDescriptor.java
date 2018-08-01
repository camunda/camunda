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
package io.zeebe.logstreams.impl.log.index;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class LogBlockIndexDescriptor {
  public static final int ENTRY_VIRTUAL_POSITION_OFFSET;

  public static final int ENTRY_PHYSICAL_POSITION_OFFSET;

  public static final int ENTRY_LENGTH;

  public static final int DATA_OFFSET;

  public static final int METADATA_OFFSET;

  public static final int INDEX_SIZE_OFFSET;

  static {
    int offset = 0;

    ENTRY_VIRTUAL_POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    ENTRY_PHYSICAL_POSITION_OFFSET = offset;
    offset += SIZE_OF_LONG;

    ENTRY_LENGTH = offset;

    offset = 2 * CACHE_LINE_LENGTH;

    METADATA_OFFSET = offset;

    offset += 2 * CACHE_LINE_LENGTH;
    INDEX_SIZE_OFFSET = offset;
    offset += 2 * CACHE_LINE_LENGTH;

    DATA_OFFSET = offset;
  }

  public static int entryLength() {
    return ENTRY_LENGTH;
  }

  public static int entryLogPositionOffset(int offset) {
    return offset + ENTRY_VIRTUAL_POSITION_OFFSET;
  }

  public static int entryAddressOffset(int offset) {
    return offset + ENTRY_PHYSICAL_POSITION_OFFSET;
  }

  public static int indexSizeOffset() {
    return INDEX_SIZE_OFFSET;
  }

  public static int entryOffset(int entryIdx) {
    return dataOffset() + (entryIdx * entryLength());
  }

  public static int dataOffset() {
    return DATA_OFFSET;
  }
}
