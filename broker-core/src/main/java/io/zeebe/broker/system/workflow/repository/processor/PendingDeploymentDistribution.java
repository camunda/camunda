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
package io.zeebe.broker.system.workflow.repository.processor;

import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;

public class PendingDeploymentDistribution {
  private final DirectBuffer deployment;
  private final long sourcePosition;
  private final ActorFuture<Void> pushFuture;

  private long distributionCount;

  public PendingDeploymentDistribution(
      DirectBuffer deployment, long sourcePosition, ActorFuture<Void> pushFuture) {
    this.deployment = deployment;
    this.sourcePosition = sourcePosition;
    this.pushFuture = pushFuture;
  }

  public void setDistributionCount(long distributionCount) {
    this.distributionCount = distributionCount;
  }

  public long decrementCount() {
    return --distributionCount;
  }

  public void complete() {
    pushFuture.complete(null);
  }

  public DirectBuffer getDeployment() {
    return deployment;
  }

  public long getSourcePosition() {
    return sourcePosition;
  }

  public ActorFuture<Void> getPushFuture() {
    return pushFuture;
  }
}
