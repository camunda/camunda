/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system;

public class SystemConstants {

  /**
   * The partition space is derived from the keyspace and the maximum bytes of long.
   *
   * <p>partitionSpace = 2^64 - KEYSPACE
   */
  public static final int PARTITION_POW_OF_2 = 13;

  public static final long PARTITION_SPACE = 1L << PARTITION_POW_OF_2;

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
   * 15_000 bytes (due to payload and so on) we can calculate the maximum events which can be
   * written to the dispatcher. `maximumEvents = maximumBytes / eventAvgSize = 1229782938247303.5`
   * We can then calculate the min pow of 2 to reach this value like: log(2, 1229782938247303.5).
   * This means we need a keyspace of 2^51 to have more keys then possible writable events.
   */
  public static final int KEYSPACE_POW_OF_2 = 51;
}
