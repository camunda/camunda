/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.distribute;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PendingDeploymentDistribution implements DbValue {

  private static final int DEPLOYMENT_LENGTH_OFFSET = SIZE_OF_LONG + SIZE_OF_INT;
  private static final int DEPLOYMENT_OFFSET = DEPLOYMENT_LENGTH_OFFSET + SIZE_OF_INT;

  private final DirectBuffer deployment;
  private long sourcePosition;
  private int distributionCount;

  public PendingDeploymentDistribution(
      DirectBuffer deployment, long sourcePosition, int distributionCount) {
    this.deployment = deployment;
    this.sourcePosition = sourcePosition;
    this.distributionCount = distributionCount;
  }

  public int decrementCount() {
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

    this.distributionCount = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

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

    buffer.putInt(offset, distributionCount, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    final int deploymentSize = deployment.capacity();
    buffer.putInt(offset, deploymentSize, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    buffer.putBytes(offset, deployment, 0, deploymentSize);
    offset += deploymentSize;

    assert (startOffset + getLength()) == offset;
  }
}
