/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class CommittedRowCacheTest {

  private final JdbcTransactionContext context = new JdbcTransactionContext();
  private final CommittedRowCache<String, String> cache = new CommittedRowCache<>(context);
  private final List<String> loads = new ArrayList<>();

  @Test
  void shouldLoadARowOnceAcrossTransactions() throws Exception {
    // given
    var transaction = context.getCurrentTransaction();
    assertThat(get("a")).isEqualTo("a-from-db");
    assertThat(get("missing")).isNull();
    transaction.commit();

    // when
    transaction = context.getCurrentTransaction();
    final var row = get("a");
    final var missing = get("missing");
    transaction.commit();

    // then
    assertThat(row).isEqualTo("a-from-db");
    assertThat(missing).isNull();
    assertThat(loads).containsExactly("a", "missing");
  }

  @Test
  void shouldKeepTheWritesOfCommittedTransactionsOnly() throws Exception {
    // given - a committed update
    var transaction = context.getCurrentTransaction();
    cache.put("a", "a1");
    transaction.commit();

    // when - an update that is rolled back
    transaction = context.getCurrentTransaction();
    cache.put("a", "a2");
    assertThat(get("a")).isEqualTo("a2");
    transaction.rollback();

    // then
    context.getCurrentTransaction();
    assertThat(get("a")).isEqualTo("a1");
    assertThat(loads).isEmpty();
  }

  @Test
  void shouldLoadADeletedRowAgain() throws Exception {
    // given
    var transaction = context.getCurrentTransaction();
    cache.put("a", "a1");
    transaction.commit();

    // when
    transaction = context.getCurrentTransaction();
    cache.remove("a");
    assertThat(get("a")).isNull();
    transaction.commit();

    // then - the deletion isn't remembered, the next read loads the row
    context.getCurrentTransaction();
    get("a");
    assertThat(loads).containsExactly("a");
  }

  private @Nullable String get(final String key) {
    return cache.get(
        key,
        k -> {
          loads.add(k);
          return k.equals("missing") ? null : k + "-from-db";
        });
  }
}
