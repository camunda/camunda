/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class DbClusterVariableKey implements DbKey {

  private final DbString name = new DbString();
  private final DbEnumValue<Scope> scope = new DbEnumValue<>(Scope.class);
  private final DbString tenantId = new DbString();
  private final DbCompositeKey<DbString, DbCompositeKey<DbEnumValue<Scope>, DbString>> key =
      new DbCompositeKey<>(name, new DbCompositeKey<>(scope, tenantId));

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (scope.getValue() == Scope.GLOBAL && tenantId.getLength() > 0) {
      throw new IllegalStateException("GLOBAL key must have empty tenantId");
    }
    key.wrap(buffer, offset, length);
  }

  public DbClusterVariableKey globalKey(final DirectBuffer nameBuf) {
    name.wrapBuffer(nameBuf);
    scope.setValue(Scope.GLOBAL);
    tenantId.wrapString("");
    return this;
  }

  public DbClusterVariableKey tenantKey(final DirectBuffer nameBuf, final String tenantId) {
    name.wrapBuffer(nameBuf);
    scope.setValue(Scope.TENANT);
    this.tenantId.wrapString(tenantId);
    return this;
  }

  @Override
  public int getLength() {
    return key.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    key.write(buffer, offset);
  }

  enum Scope {
    GLOBAL,
    TENANT
  }
}
