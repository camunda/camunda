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
package io.zeebe.broker.workflow.deployment.distribute.processor.state;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;

import io.zeebe.broker.util.KeyStateController;
import io.zeebe.broker.workflow.deployment.distribute.processor.PendingDeploymentDistribution;
import io.zeebe.broker.workflow.state.PersistenceHelper;
import io.zeebe.logstreams.rocksdb.ZbRocksDb;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class DeploymentsStateController extends KeyStateController {
  private static final byte[] PENDING_DEPLOYMENT_COLUMN_FAMILY_NAME =
      "pendingDeployment".getBytes();

  public static final byte[][] COLUMN_FAMILY_NAMES = {PENDING_DEPLOYMENT_COLUMN_FAMILY_NAME};

  private final PendingDeploymentDistribution pendingDeploymentDistribution;
  private final MutableDirectBuffer valueBuffer;
  private final PersistenceHelper persistenceHelper;

  private ZbRocksDb db;
  private ColumnFamilyHandle pendingDeploymentColumnFamily;

  public DeploymentsStateController() {
    persistenceHelper = new PersistenceHelper(this);
    pendingDeploymentDistribution = new PendingDeploymentDistribution(new UnsafeBuffer(0, 0), -1);
    valueBuffer = new ExpandableArrayBuffer();
  }

  @Override
  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    final List<byte[]> columnFamilyNames =
        Stream.of(COLUMN_FAMILY_NAMES).flatMap(Stream::of).collect(Collectors.toList());

    final RocksDB rocksDB = super.open(dbDirectory, reopen, columnFamilyNames);
    pendingDeploymentColumnFamily = getColumnFamilyHandle(PENDING_DEPLOYMENT_COLUMN_FAMILY_NAME);

    return rocksDB;
  }

  @Override
  protected RocksDB openDb(DBOptions dbOptions) throws RocksDBException {
    db =
        ZbRocksDb.open(
            dbOptions, dbDirectory.getAbsolutePath(), columnFamilyDescriptors, columnFamilyHandles);
    return db;
  }

  public void putPendingDeployment(
      final long key, final PendingDeploymentDistribution pendingDeploymentDistribution) {

    final int length = pendingDeploymentDistribution.getLength();
    pendingDeploymentDistribution.write(valueBuffer, 0);

    put(pendingDeploymentColumnFamily, key, valueBuffer.byteArray(), 0, length);
  }

  private PendingDeploymentDistribution getPending(final long key) {
    setLong(key);
    final boolean successfulRead =
        persistenceHelper.readInto(
            pendingDeploymentDistribution,
            pendingDeploymentColumnFamily,
            dbLongBuffer.byteArray(),
            0,
            Long.BYTES);

    return successfulRead ? pendingDeploymentDistribution : null;
  }

  public PendingDeploymentDistribution getPendingDeployment(final long key) {
    return getPending(key);
  }

  public PendingDeploymentDistribution removePendingDeployment(final long key) {
    final PendingDeploymentDistribution pending = getPending(key);
    if (pending != null) {
      delete(key);
    }
    return pending;
  }

  public void foreachPending(final BiConsumer<Long, PendingDeploymentDistribution> consumer) {
    db.forEach(
        pendingDeploymentColumnFamily,
        (zbRocksEntry, iteratorControl) -> {
          final DirectBuffer value = zbRocksEntry.getValue();
          pendingDeploymentDistribution.wrap(value, 0, value.capacity());
          consumer.accept(
              zbRocksEntry.getKey().getLong(0, STATE_BYTE_ORDER), pendingDeploymentDistribution);
        });
  }
}
