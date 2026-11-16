/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class AuthorizationKeys extends MsgpackBufferValue<AuthorizationKeys>
    implements DbValue {

  final LongArrayList authorizationKeys = new LongArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public Set<Long> getAuthorizationKeys() {
    return authorizationKeys.stream().collect(Collectors.toSet());
  }

  public void setAuthorizationKeys(final Set<Long> keys) {
    authorizationKeys.clear();
    keys.forEach(key -> authorizationKeys.addLong(key));
  }

  public void addAuthorizationKey(final long authorizationKey) {
    authorizationKeys.addLong(authorizationKey);
  }

  public void removeAuthorizationKey(final long authorizationKey) {
    final var keys = getAuthorizationKeys();
    keys.remove(authorizationKey);
    setAuthorizationKeys(keys);
  }

  @Override
  protected MsgpackCodec<AuthorizationKeys> codec() {
    return AuthorizationKeysCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AuthorizationKeys> ops() {
    return AuthorizationKeysOps.INSTANCE;
  }
}
