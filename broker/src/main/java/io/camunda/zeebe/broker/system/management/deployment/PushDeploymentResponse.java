/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.management.deployment;

import io.zeebe.clustering.management.PushDeploymentResponseDecoder;
import io.zeebe.clustering.management.PushDeploymentResponseEncoder;
import io.zeebe.protocol.impl.encoding.SbeBufferWriterReader;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public final class PushDeploymentResponse
    extends SbeBufferWriterReader<PushDeploymentResponseEncoder, PushDeploymentResponseDecoder> {

  private final PushDeploymentResponseEncoder bodyEncoder = new PushDeploymentResponseEncoder();
  private final PushDeploymentResponseDecoder bodyDecoder = new PushDeploymentResponseDecoder();

  private int partitionId = PushDeploymentResponseEncoder.partitionIdNullValue();
  private long deploymentKey = PushDeploymentResponseEncoder.deploymentKeyNullValue();

  public PushDeploymentResponse partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public int partitionId() {
    return partitionId;
  }

  public PushDeploymentResponse deploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public long deploymentKey() {
    return deploymentKey;
  }

  @Override
  protected PushDeploymentResponseEncoder getBodyEncoder() {
    return bodyEncoder;
  }

  @Override
  protected PushDeploymentResponseDecoder getBodyDecoder() {
    return bodyDecoder;
  }

  @Override
  public void reset() {
    super.reset();

    partitionId = PushDeploymentResponseEncoder.partitionIdNullValue();
    deploymentKey = PushDeploymentResponseEncoder.deploymentKeyNullValue();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    super.write(buffer, offset);

    bodyEncoder.partitionId(partitionId).deploymentKey(deploymentKey);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    super.wrap(buffer, offset, length);

    partitionId = bodyDecoder.partitionId();
    deploymentKey = bodyDecoder.deploymentKey();
  }
}
