/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.user;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedUser extends MsgpackBufferValue<PersistedUser> implements DbValue {

  final UserRecord user = new UserRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PersistedUser copy() {
    final var copy = new PersistedUser();
    copy.copyFrom(this);
    return copy;
  }

  public UserRecord getUser() {
    return user;
  }

  public void setUser(final UserRecord record) {
    user.copyFrom(record);
  }

  public long getUserKey() {
    return getUser().getUserKey();
  }

  public String getUsername() {
    return getUser().getUsername();
  }

  public String getName() {
    return getUser().getName();
  }

  public String getEmail() {
    return getUser().getEmail();
  }

  public String getPassword() {
    return getUser().getPassword();
  }

  @Override
  protected MsgpackCodec<PersistedUser> codec() {
    return PersistedUserCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedUser> ops() {
    return PersistedUserOps.INSTANCE;
  }
}
