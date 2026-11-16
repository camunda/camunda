/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class DistributionMetadata extends MsgpackBufferValue<DistributionMetadata>
    implements DbValue {

  long startTime = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getStartTime() {
    return startTime;
  }

  public DistributionMetadata setStartTime(final long startTime) {
    this.startTime = startTime;
    return this;
  }

  @Override
  protected MsgpackCodec<DistributionMetadata> codec() {
    return DistributionMetadataCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DistributionMetadata> ops() {
    return DistributionMetadataOps.INSTANCE;
  }
}
