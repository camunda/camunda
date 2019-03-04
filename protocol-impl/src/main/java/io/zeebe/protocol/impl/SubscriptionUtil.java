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
package io.zeebe.protocol.impl;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import org.agrona.DirectBuffer;

public class SubscriptionUtil {

  /**
   * Get the hash code of the subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @return the hash code of the subscription
   */
  protected static int getSubscriptionHashCode(DirectBuffer correlationKey) {
    // is equal to java.lang.String#hashCode
    int hashCode = 0;

    for (int i = 0, length = correlationKey.capacity(); i < length; i++) {
      hashCode = 31 * hashCode + correlationKey.getByte(i);
    }
    return hashCode;
  }

  /**
   * Get the partition id for message subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @param partitionCount the number of partitions
   * @return the partition id for the subscription
   */
  public static int getSubscriptionPartitionId(DirectBuffer correlationKey, int partitionCount) {
    final int hashCode = getSubscriptionHashCode(correlationKey);
    // partition ids range from START_PARTITION_ID .. START_PARTITION_ID + partitionCount
    return Math.abs(hashCode % partitionCount) + START_PARTITION_ID;
  }
}
