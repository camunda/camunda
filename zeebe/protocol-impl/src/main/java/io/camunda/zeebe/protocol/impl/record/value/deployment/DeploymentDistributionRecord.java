/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import org.jspecify.annotations.NullMarked;

/**
 * DeploymentDistributionRecord is deprecated as of 8.2.0. A generalised way of distributing
 * commands has been introduced in this version. Distribution should now be handled using the {@link
 * io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord} This record
 * only remains to stay backwards compatible.
 */
@Deprecated
@MsgpackPojo
@NullMarked
public final class DeploymentDistributionRecord
    extends MsgpackBufferValue<DeploymentDistributionRecord>
    implements MsgpackRecordValue, DeploymentDistributionRecordValue {

  final RequiredInt partitionId = new RequiredInt();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public int getPartitionId() {
    return partitionId.get();
  }

  public void setPartition(final int partitionId) {
    this.partitionId.set(partitionId);
  }

  @Override
  protected MsgpackCodec<DeploymentDistributionRecord> codec() {
    return DeploymentDistributionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DeploymentDistributionRecord> ops() {
    return DeploymentDistributionRecordOps.INSTANCE;
  }
}
