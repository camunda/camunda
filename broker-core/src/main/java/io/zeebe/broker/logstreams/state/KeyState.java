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
package io.zeebe.broker.logstreams.state;

import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.workflow.state.NextValueManager;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.logstreams.state.StateLifecycleListener;
import io.zeebe.protocol.Protocol;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.rocksdb.ColumnFamilyHandle;

public class KeyState implements StateLifecycleListener, KeyGenerator {

  private static final long INITIAL_VALUE = 0;

  private static final byte[] KEY_FAMILY_NAME = "key".getBytes();
  private static final byte[] LATEST_KEY = "latestKey".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {KEY_FAMILY_NAME};

  public static List<byte[]> getColumnFamilyNames() {
    return Stream.of(COLUMN_FAMILY_NAMES).flatMap(Stream::of).collect(Collectors.toList());
  }

  private final long keyStartValue;

  private ColumnFamilyHandle keyHandle;
  private NextValueManager nextValueManager;

  /**
   * Initializes the key state with the corresponding partition id, so that unique keys are
   * generated over all partitions.
   *
   * @param partitionId the partition to determine the key start value
   */
  public KeyState(int partitionId) {
    keyStartValue = Protocol.encodePartitionId(partitionId, INITIAL_VALUE);
  }

  @Override
  public void onOpened(StateController stateController) {
    keyHandle = stateController.getColumnFamilyHandle(KEY_FAMILY_NAME);

    nextValueManager = new NextValueManager(stateController, keyStartValue);
  }

  @Override
  public long nextKey() {
    return nextValueManager.getNextValue(keyHandle, LATEST_KEY);
  }
}
