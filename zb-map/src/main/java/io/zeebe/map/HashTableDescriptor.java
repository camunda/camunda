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
package io.zeebe.map;

import static org.agrona.BitUtil.SIZE_OF_INT;

/**
 * The ZbMap has 2 Buffers: the "hash table buffer" and the "buckets buffer".
 *
 * <p>Hash table buffer layout
 *
 * <pre>
 *  0               1               2               3
 *  0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                             MAP LENGTH                      |
 *  +-------------------------------------------------------------+
 *  |                                                             |
 *  |                             MAP DATA                       ...
 * ...                                                            |
 *  +-------------------------------------------------------------+
 * </pre>
 */
public class HashTableDescriptor {
  public static final int HASH_TABLE_SIZE_OFFSET;
  public static final int HASH_TABLE_OFFSET;

  static {
    int offset = 0;

    HASH_TABLE_SIZE_OFFSET = offset;
    offset += SIZE_OF_INT;

    HASH_TABLE_OFFSET = offset;
  }
}
