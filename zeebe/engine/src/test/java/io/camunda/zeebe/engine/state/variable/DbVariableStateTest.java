/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbVariableStateTest {

  private static final long PROCESS_DEFINITION_KEY = 123L;

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  @SuppressWarnings("unused") // injected by the extension
  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  @SuppressWarnings("unused") // injected by the extension
  private TransactionContext transactionContext;

  private final DbLong scopeKey = new DbLong();

  private MutableVariableState variableState;
  private ColumnFamily<DbLong, PersistedVariableNames> variableNamesByScopeKeyColumnFamily;
  private long nextVariableKey;

  @BeforeEach
  void setUp() {
    variableState = processingState.getVariableState();
    variableNamesByScopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.VARIABLE_NAMES_BY_SCOPE_KEY,
            transactionContext,
            scopeKey,
            new PersistedVariableNames());
    nextVariableKey = 1L;
  }

  @Test
  void shouldTrackVariableNamesPerScopeAndDeleteThemOnRemoveAllVariables() {
    // given
    final long variableScopeKey = 1L;
    variableState.createScope(variableScopeKey, VariableState.NO_PARENT);
    setVariableLocal(variableScopeKey, "first", "1");
    setVariableLocal(variableScopeKey, "second", "2");

    // then
    assertThat(getTrackedVariableNames(variableScopeKey))
        .containsExactlyInAnyOrder("first", "second");

    // when
    variableState.removeAllVariables(variableScopeKey);

    // then
    assertThat(variableState.getVariablesLocal(variableScopeKey)).isEmpty();
    assertThat(getTrackedVariableNames(variableScopeKey)).isEmpty();
  }

  @Test
  void shouldTrackVariableNameOnlyOncePerScope() {
    // given
    final long variableScopeKey = 1L;
    variableState.createScope(variableScopeKey, VariableState.NO_PARENT);
    setVariableLocal(variableScopeKey, "first", "1");
    setVariableLocal(variableScopeKey, "first", "2");

    // then
    assertThat(getTrackedVariableNames(variableScopeKey)).containsExactly("first");
  }

  private void setVariableLocal(final long scopeKey, final String name, final String value) {
    variableState.setVariableLocal(
        nextVariableKey++, scopeKey, PROCESS_DEFINITION_KEY, wrapString(name), asMsgPack(value));
  }

  private List<String> getTrackedVariableNames(final long scopeKey) {
    this.scopeKey.wrapLong(scopeKey);

    final var trackedVariableNames =
        variableNamesByScopeKeyColumnFamily.get(this.scopeKey, PersistedVariableNames::new);

    if (trackedVariableNames == null) {
      return List.of();
    }

    return trackedVariableNames.getVariableNames().stream()
        .map(buffer -> bufferAsString(buffer))
        .toList();
  }
}
