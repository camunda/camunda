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

import io.zeebe.broker.logstreams.state.ZbColumnFamilies;
import io.zeebe.broker.workflow.deployment.distribute.processor.PendingDeploymentDistribution;
import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import java.util.function.ObjLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

public class DeploymentsState {
  private final PendingDeploymentDistribution pendingDeploymentDistribution;

  private final DbLong deploymentKey;
  private final ColumnFamily<DbLong, PendingDeploymentDistribution> pendingDeploymentColumnFamily;

  public DeploymentsState(ZeebeDb<ZbColumnFamilies> zeebeDb) {

    deploymentKey = new DbLong();
    pendingDeploymentDistribution = new PendingDeploymentDistribution(new UnsafeBuffer(0, 0), -1);
    pendingDeploymentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DEPLOYMENT, deploymentKey, pendingDeploymentDistribution);
  }

  public void putPendingDeployment(
      final long key, final PendingDeploymentDistribution pendingDeploymentDistribution) {

    deploymentKey.wrapLong(key);
    pendingDeploymentColumnFamily.put(deploymentKey, pendingDeploymentDistribution);
  }

  private PendingDeploymentDistribution getPending(final long key) {
    deploymentKey.wrapLong(key);
    return pendingDeploymentColumnFamily.get(deploymentKey);
  }

  public PendingDeploymentDistribution getPendingDeployment(final long key) {
    return getPending(key);
  }

  public PendingDeploymentDistribution removePendingDeployment(final long key) {
    final PendingDeploymentDistribution pending = getPending(key);
    if (pending != null) {
      pendingDeploymentColumnFamily.delete(deploymentKey);
    }
    return pending;
  }

  public void foreachPending(final ObjLongConsumer<PendingDeploymentDistribution> consumer) {

    pendingDeploymentColumnFamily.forEach(
        (deploymentKey, pendingDeployment) ->
            consumer.accept(pendingDeployment, deploymentKey.getValue()));
  }
}
