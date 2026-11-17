/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class LatestProcessVersion extends MsgpackBufferValue<LatestProcessVersion>
    implements DbValue {
  final RequiredLong latestProcessVersion = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void set(final long latestVersion) {
    latestProcessVersion.set(latestVersion);
  }

  public long get() {
    return latestProcessVersion.get();
  }

  @Override
  protected MsgpackCodec<LatestProcessVersion> codec() {
    return LatestProcessVersionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<LatestProcessVersion> ops() {
    return LatestProcessVersionOps.INSTANCE;
  }
}
