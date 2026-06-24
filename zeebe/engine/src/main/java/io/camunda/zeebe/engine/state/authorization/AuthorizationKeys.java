/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class AuthorizationKeys extends UnpackedObject implements DbValue {
  private final ArrayProperty<LongValue> authorizationKeys =
      new ArrayProperty<>("authorizationKeys", LongValue::new);

  public AuthorizationKeys() {
    super(1);
    declareProperty(authorizationKeys);
  }

  public Set<Long> getAuthorizationKeys() {
    return StreamSupport.stream(authorizationKeys.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toSet());
  }

  public void setAuthorizationKeys(final Set<Long> keys) {
    authorizationKeys.reset();
    keys.forEach(key -> authorizationKeys.add().setValue(key));
  }

  public void addAuthorizationKey(final long authorizationKey) {
    authorizationKeys.add().setValue(authorizationKey);
  }

  public void removeAuthorizationKey(final long authorizationKey) {
    final var keys = getAuthorizationKeys();
    keys.remove(authorizationKey);
    setAuthorizationKeys(keys);
  }
}
