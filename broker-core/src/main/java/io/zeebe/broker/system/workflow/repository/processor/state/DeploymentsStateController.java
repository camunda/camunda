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
package io.zeebe.broker.system.workflow.repository.processor.state;

import io.zeebe.broker.system.workflow.repository.processor.PendingDeploymentDistribution;
import io.zeebe.broker.util.KeyStateController;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.RocksIterator;

public class DeploymentsStateController extends KeyStateController {

  private final PendingDeploymentDistribution pendingDeploymentDistribution;
  private final UnsafeBuffer buffer;

  public DeploymentsStateController() {
    pendingDeploymentDistribution = new PendingDeploymentDistribution(new UnsafeBuffer(0, 0), -1);
    buffer = new UnsafeBuffer(0, 0);
  }

  public void putPendingDeployment(
      long key, PendingDeploymentDistribution pendingDeploymentDistribution) {
    ensureIsOpened("putPendingDeployment");

    final int length = pendingDeploymentDistribution.getLength();
    final byte[] bytes = new byte[length];
    buffer.wrap(bytes);
    pendingDeploymentDistribution.write(buffer, 0);

    put(key, bytes);
  }

  private PendingDeploymentDistribution getPending(long key) {
    final byte[] bytes = get(key);
    PendingDeploymentDistribution pending = null;
    if (bytes != null) {
      buffer.wrap(bytes);
      pendingDeploymentDistribution.wrap(buffer, 0, bytes.length);
      pending = pendingDeploymentDistribution;
    }

    return pending;
  }

  public PendingDeploymentDistribution getPendingDeployment(long key) {
    ensureIsOpened("getPendingDeployment");
    return getPending(key);
  }

  public PendingDeploymentDistribution removePendingDeployment(long key) {
    ensureIsOpened("removePendingDeployment");

    final PendingDeploymentDistribution pending = getPending(key);
    if (pending != null) {
      delete(key);
    }

    return pending;
  }

  public void foreach(BiConsumer<Long, PendingDeploymentDistribution> consumer) {
    ensureIsOpened("foreach");

    try (final RocksIterator rocksIterator = getDb().newIterator()) {
      rocksIterator.seekToFirst();

      final UnsafeBuffer latestKeyBuffer = new UnsafeBuffer(KeyStateController.LATEST_KEY_BUFFER);
      final UnsafeBuffer readBuffer = new UnsafeBuffer();
      while (rocksIterator.isValid()) {

        final byte[] keyBytes = rocksIterator.key();
        readBuffer.wrap(keyBytes);

        if (!latestKeyBuffer.equals(readBuffer)) {
          final long longKey = readBuffer.getLong(0, ByteOrder.LITTLE_ENDIAN);

          final byte[] valueBytes = rocksIterator.value();
          readBuffer.wrap(valueBytes);

          pendingDeploymentDistribution.wrap(readBuffer, 0, valueBytes.length);

          consumer.accept(longKey, pendingDeploymentDistribution);
        }
        rocksIterator.next();
      }
    }
  }
}
