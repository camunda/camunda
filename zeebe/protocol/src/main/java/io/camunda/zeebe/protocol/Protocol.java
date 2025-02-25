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
package io.camunda.zeebe.protocol;

import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import java.nio.ByteOrder;

public final class Protocol {

  public static final int PROTOCOL_VERSION = ExecuteCommandRequestDecoder.SCHEMA_VERSION;

  /**
   * The endianness of multibyte values encoded in the protocol. This MUST match the default byte
   * order in the SBE XML schema.
   */
  public static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

  /** By convention, the partition to deploy to */
  public static final int DEPLOYMENT_PARTITION = 1;

  /**
   * Id of the first partition. partition ids are in the range(START_PARTITION_ID,
   * START_PARTITION_ID + partitionCount)
   */
  public static final int START_PARTITION_ID = 1;

  /**
   * The partition space is derived from the keyspace and the maximum bytes of long.
   *
   * <p>partitionSpace = 2^64 - KEYSPACE
   */
  public static final int PARTITION_BITS = 13;

  /** The maximum number of partitions that can be created in a cluster, i.e. 2^13, or 8192. */
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

  /** Job typ used for user tasks handled by Camunda Cloud Tasklist */
  public static final String USER_TASK_JOB_TYPE = "io.camunda.zeebe:userTask";

  /** Prefix for key of reserved task headers */
  public static final String RESERVED_HEADER_NAME_PREFIX = "io.camunda.zeebe:";

  /** Task header key used for the action associated with the user task */
  public static final String USER_TASK_ACTION_HEADER_NAME = RESERVED_HEADER_NAME_PREFIX + "action";

  /** Task header key used for assignee */
  public static final String USER_TASK_ASSIGNEE_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "assignee";

  /** Task header key used for candidate groups */
  public static final String USER_TASK_CANDIDATE_GROUPS_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "candidateGroups";

  /** Task header key used for candidate users */
  public static final String USER_TASK_CANDIDATE_USERS_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "candidateUsers";

  /** Task header key used for due date */
  public static final String USER_TASK_DUE_DATE_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "dueDate";

  /** Task header key used for follow-up date */
  public static final String USER_TASK_FOLLOW_UP_DATE_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "followUpDate";

  /** Task header key used for user tasks to contain form key from BPMN XML */
  public static final String USER_TASK_FORM_KEY_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "formKey";

  /** Task header key used for the unique user task key. */
  public static final String USER_TASK_KEY_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "userTaskKey";

  /** Task header key used for the priority of the user task */
  public static final String USER_TASK_PRIORITY_HEADER_NAME =
      RESERVED_HEADER_NAME_PREFIX + "priority";

  /** Linked resources header used in service task */
  public static final String LINKED_RESOURCES_HEADER_NAME = "linkedResources";

  public static long encodePartitionId(final int partitionId, final long key) {
    return ((long) partitionId << KEY_BITS) + key;
  }

  public static int decodePartitionId(final long key) {
    return (int) (key >> KEY_BITS);
  }

  public static long decodeKeyInPartition(final long key) {
    // For comprehension, we calculate: key - ((long) partitionId << KEY_BITS);

    // for efficiency we do it as a bit-wise operation
    return key & 0x0007FFFFFFFFFFFFL;
  }
}
