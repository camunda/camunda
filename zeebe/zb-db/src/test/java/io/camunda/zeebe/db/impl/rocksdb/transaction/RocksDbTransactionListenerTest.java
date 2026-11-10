/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocksDbTransactionListenerTest {

  private final RecordingListener listener = new RecordingListener();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private TransactionContext transactionContext;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    zeebeDb =
        DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory().createDb(tempDir.toFile());
    transactionContext = zeebeDb.createContext();
    transactionContext.addTransactionListener(listener);
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldNotifyCommitOfCurrentTransaction() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    transaction.run(() -> {});

    // when
    transaction.commit();

    // then
    assertThat(listener.outcomes).containsExactly("commit");
  }

  @Test
  void shouldNotifyRollbackOfCurrentTransaction() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();
    transaction.run(() -> {});

    // when
    transaction.rollback();

    // then
    assertThat(listener.outcomes).containsExactly("rollback");
  }

  @Test
  void shouldNotifyOnlyCommitOfSuccessfulRunInTransaction() {
    // when
    transactionContext.runInTransaction(() -> {});

    // then
    assertThat(listener.outcomes).containsExactly("commit");
  }

  @Test
  void shouldNotifyRollbackOfFailedRunInTransaction() {
    // when
    assertThatThrownBy(
            () ->
                transactionContext.runInTransaction(
                    () -> {
                      throw new IllegalStateException("expected");
                    }))
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThat(listener.outcomes).containsExactly("rollback");
  }

  @Test
  void shouldNotNotifyNestedRunInTransaction() throws Exception {
    // given
    final var transaction = transactionContext.getCurrentTransaction();

    // when
    transaction.run(() -> transactionContext.runInTransaction(() -> {}));

    // then
    assertThat(listener.outcomes).isEmpty();
    transaction.commit();
    assertThat(listener.outcomes).containsExactly("commit");
  }

  private static final class RecordingListener implements TransactionListener {
    private final List<String> outcomes = new ArrayList<>();

    @Override
    public void onCommit() {
      outcomes.add("commit");
    }

    @Override
    public void onRollback() {
      outcomes.add("rollback");
    }
  }
}
