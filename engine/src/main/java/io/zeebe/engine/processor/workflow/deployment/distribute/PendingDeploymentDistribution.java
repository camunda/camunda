/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.distribute;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PendingDeploymentDistribution implements DbValue {

  private static final int DEPLOYMENT_LENGTH_OFFSET = SIZE_OF_LONG;
  private static final int DEPLOYMENT_OFFSET = DEPLOYMENT_LENGTH_OFFSET + SIZE_OF_INT;

  private final DirectBuffer deployment;
  private long sourcePosition;
  private long distributionCount;

  public PendingDeploymentDistribution(DirectBuffer deployment, long sourcePosition) {
    this.deployment = deployment;
    this.sourcePosition = sourcePosition;
  }

  public void setDistributionCount(long distributionCount) {
    this.distributionCount = distributionCount;
  }

  public long decrementCount() {
    return --distributionCount;
  }

  public DirectBuffer getDeployment() {
    return deployment;
  }

  public long getSourcePosition() {
    return sourcePosition;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    this.sourcePosition = buffer.getLong(offset, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    final int deploymentSize = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    deployment.wrap(buffer, offset, deploymentSize);
  }

  @Override
  public int getLength() {
    final int deploymentSize = deployment.capacity();
    final int length = DEPLOYMENT_OFFSET + deploymentSize;
    return length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    final int startOffset = offset;
    buffer.putLong(offset, sourcePosition, ZB_DB_BYTE_ORDER);
    offset += Long.BYTES;

    final int deploymentSize = deployment.capacity();
    buffer.putInt(offset, deploymentSize, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putBytes(offset, deployment, 0, deploymentSize);
    offset += deploymentSize;

    assert (startOffset + getLength()) == offset;
  }
}
