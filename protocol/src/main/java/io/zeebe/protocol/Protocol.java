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
package io.zeebe.protocol;

import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import java.nio.ByteOrder;

public class Protocol {

  public static final int PROTOCOL_VERSION = ExecuteCommandRequestDecoder.SCHEMA_VERSION;

  /**
   * The endianness of multibyte values encoded in the protocol. This MUST match the default byte
   * order in the SBE XML schema.
   */
  public static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

  /** The null value of an instant property which indicates that it is not set. */
  public static final long INSTANT_NULL_VALUE = Long.MIN_VALUE;

  /** By convention, the partition to deploy to */
  public static final int DEPLOYMENT_PARTITION = 1;

  /**
   * Id of the first partition. partition ids are in the range(START_PARTITION_ID,
   * START_PARTITION_ID + partitionCount)
   */
  public static final int START_PARTITION_ID = 1;

  public static final long encodePartitionId(int partitionId, long key) {
    return ((long) partitionId << KEY_BITS) + key;
  }

  public static final int decodePartitionId(long key) {
    return (int) (key >> KEY_BITS);
  }

  /**
   * The partition space is derived from the keyspace and the maximum bytes of long.
   *
   * <p>partitionSpace = 2^64 - KEYSPACE
   */
  public static final int PARTITION_BITS = 13;

  public static final long MAXIMUM_PARTITIONS = 1L << PARTITION_BITS;

  /**
   * Keyspace is defined for each partition. To define the keyspace size, the maximum events, which
   * can be written to the dispatcher implementation, has to be calculated.
   *
   * <p><b> If we change or replace the dispatcher implementation we should check if the current
   * defined key space size is still valid. </b>
   *
   * <p>Calculation is done as follows:
   *
   * <p>On each segment 2^32 bytes can be written, we can have 2^32 segments. This means we can at
   * maximum write 2*32 * 2^32 = 18446744073709551616 bytes. If we assume an avg event size of
   * 15_000 bytes (due to variables and so on) we can calculate the maximum events which can be
   * written to the dispatcher. `maximumEvents = maximumBytes / eventAvgSize = 1229782938247303.5`
   * We can then calculate the min pow of 2 to reach this value like: log(2, 1229782938247303.5).
   * This means we need a keyspace of 2^51 to have more keys then possible writable events.
   */
  public static final int KEY_BITS = 51;
}
