/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clustervariable;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbEnumValue;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DbClusterVariableScopeKey implements DbKey {

  private final DbEnumValue<ClusterVariableScope> scope;
  private final DbString scopeKey;
  private final DirectBuffer emptyScopeKey = new UnsafeBuffer(0, 0);

  public DbClusterVariableScopeKey(
      final DbEnumValue<ClusterVariableScope> scope, final DbString scopeKey) {
    this.scope = scope;
    this.scopeKey = scopeKey;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    scope.wrap(buffer, offset, length);

    if (scope.getValue() != ClusterVariableScope.GLOBAL) {
      final var scopeLength = scope.getLength();
      scopeKey.wrap(buffer, offset + scopeLength, length - scopeLength);
    } else {
      scopeKey.wrapBuffer(emptyScopeKey);
    }
  }

  @Override
  public int getLength() {
    final var tenantLength =
        scope.getValue() == ClusterVariableScope.GLOBAL ? 0 : scopeKey.getLength();
    return scope.getLength() + tenantLength;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    int written = scope.write(buffer, offset);

    if (scope.getValue() != ClusterVariableScope.GLOBAL) {
      written += scopeKey.write(buffer, offset + written);
    } // else don't write scopeKey as part of the key
    return written;
  }
}
