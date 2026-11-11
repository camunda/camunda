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
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class LatestProcessVersion extends MsgpackBufferValue<LatestProcessVersion>
    implements DbValue {

  long latestProcessVersion;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public LatestProcessVersion() {}

  public void set(final long latestVersion) {
    latestProcessVersion = latestVersion;
  }

  public long get() {
    return latestProcessVersion;
  }
}
