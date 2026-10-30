/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.instance.ParentScopeKey;
import io.camunda.zeebe.engine.state.instance.VariableDocumentState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

final class DbVariableStateTest {

  private static final long CHILD_SCOPE_KEY = 1L;
  private static final long PARENT_SCOPE_KEY = 2L;

  @Test
  void shouldStopVisitingParentScopesWhenRequestedVariablesAreCollected() {
    // given
    final ZeebeDb<ZbColumnFamilies> zeebeDb = mock(ZeebeDb.class);
    final TransactionContext transactionContext = mock(TransactionContext.class);
    final ColumnFamily<DbLong, ParentScopeKey> childParentColumnFamily = mock(ColumnFamily.class);
    final ColumnFamily<DbCompositeKey<DbLong, DbString>, VariableInstance> variableColumnFamily =
        mock(ColumnFamily.class);
    final ColumnFamily<DbLong, VariableDocumentState> variableDocumentColumnFamily =
        mock(ColumnFamily.class);

    when(zeebeDb.createColumnFamily(
            eq(ZbColumnFamilies.ELEMENT_INSTANCE_CHILD_PARENT),
            eq(transactionContext),
            any(DbLong.class),
            any(ParentScopeKey.class)))
        .thenReturn(childParentColumnFamily);
    when(zeebeDb.createColumnFamily(
            eq(ZbColumnFamilies.VARIABLES),
            eq(transactionContext),
            org.mockito.ArgumentMatchers.<DbCompositeKey<DbLong, DbString>>any(),
            any(VariableInstance.class)))
        .thenReturn(variableColumnFamily);
    when(zeebeDb.createColumnFamily(
            eq(ZbColumnFamilies.VARIABLE_DOCUMENT_STATE_BY_SCOPE_KEY),
            eq(transactionContext),
            any(DbLong.class),
            any(VariableDocumentState.class)))
        .thenReturn(variableDocumentColumnFamily);

    final var parentScope = new ParentScopeKey();
    parentScope.set(PARENT_SCOPE_KEY);
    final var noParentScope = new ParentScopeKey();
    noParentScope.set(-1L);
    when(childParentColumnFamily.get(any()))
        .thenAnswer(
            invocation ->
                ((DbLong) invocation.getArgument(0)).getValue() == CHILD_SCOPE_KEY
                    ? parentScope
                    : noParentScope);

    final var variableKey = new DbCompositeKey<>(new DbLong(), new DbString());
    variableKey.first().wrapLong(CHILD_SCOPE_KEY);
    variableKey.second().wrapString("requested");
    final var variableValue = asMsgPack("\"value\"");
    final var variable =
        new VariableInstance().setValue(variableValue, 0, variableValue.capacity());
    final var scopeIterations = new AtomicInteger();
    doAnswer(
            invocation -> {
              scopeIterations.incrementAndGet();
              final KeyValuePairVisitor<DbCompositeKey<DbLong, DbString>, VariableInstance>
                  visitor = invocation.getArgument(1);
              visitor.visit(variableKey, variable);
              return null;
            })
        .when(variableColumnFamily)
        .whileEqualPrefix(
            any(),
            ArgumentMatchers
                .<KeyValuePairVisitor<DbCompositeKey<DbLong, DbString>, VariableInstance>>any());

    final var variableState = new DbVariableState(zeebeDb, transactionContext);

    // when
    final var variablesDocument =
        variableState.getVariablesAsDocument(CHILD_SCOPE_KEY, List.of(wrapString("requested")));

    // then
    assertEquality(variablesDocument, "{'requested': 'value'}");
    assertThat(scopeIterations).hasValue(1);
  }
}
