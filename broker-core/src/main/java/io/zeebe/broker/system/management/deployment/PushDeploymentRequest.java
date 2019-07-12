/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.management.deployment;

import static io.zeebe.clustering.management.PushDeploymentRequestDecoder.deploymentHeaderLength;

import io.zeebe.clustering.management.PushDeploymentRequestDecoder;
import io.zeebe.clustering.management.PushDeploymentRequestEncoder;
import io.zeebe.engine.util.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PushDeploymentRequest
    extends SbeBufferWriterReader<PushDeploymentRequestEncoder, PushDeploymentRequestDecoder> {

  private final PushDeploymentRequestEncoder bodyEncoder = new PushDeploymentRequestEncoder();
  private final PushDeploymentRequestDecoder bodyDecoder = new PushDeploymentRequestDecoder();

  private int partitionId = PushDeploymentRequestEncoder.partitionIdNullValue();
  private long deploymentKey = PushDeploymentRequestEncoder.deploymentKeyNullValue();
  private final DirectBuffer deployment = new UnsafeBuffer(0, 0);

  public PushDeploymentRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public int partitionId() {
    return this.partitionId;
  }

  public PushDeploymentRequest deploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public long deploymentKey() {
    return this.deploymentKey;
  }

  public PushDeploymentRequest deployment(final DirectBuffer directBuffer) {
    deployment.wrap(directBuffer);
    return this;
  }

  public DirectBuffer deployment() {
    return this.deployment;
  }

  @Override
  protected PushDeploymentRequestEncoder getBodyEncoder() {
    return bodyEncoder;
  }

  @Override
  protected PushDeploymentRequestDecoder getBodyDecoder() {
    return bodyDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    partitionId = bodyDecoder.partitionId();
    deploymentKey = bodyDecoder.deploymentKey();

    deployment.wrap(
        buffer, bodyDecoder.limit() + deploymentHeaderLength(), bodyDecoder.deploymentLength());
  }

  @Override
  public int getLength() {
    return super.getLength()
        + PushDeploymentRequestEncoder.deploymentHeaderLength()
        + deployment.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    bodyEncoder
        .partitionId(partitionId)
        .deploymentKey(deploymentKey)
        .putDeployment(deployment, 0, deployment.capacity());
  }

  public void reset() {
    super.reset();

    partitionId = PushDeploymentRequestEncoder.partitionIdNullValue();
    deploymentKey = PushDeploymentRequestEncoder.deploymentKeyNullValue();
    deployment.wrap(0, 0);
  }
}
