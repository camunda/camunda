/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import io.camunda.zeebe.broker.logstreams.rdbms.RdbmsLogStorage.WriterConfig;
import io.camunda.zeebe.logstreams.storage.LogStorage.AppendListener;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs {@link RdbmsLogStorage} against the real schema and {@link JdbcLogBlockRepository}. */
final class RdbmsLogStorageH2Test {

  private static final String PREFIX = "TEST_";
  private static final long TERM = 1;

  private JdbcLogBlockRepository repository;
  private RdbmsLogStorage storage;

  @BeforeEach
  void setUp() throws Exception {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:log-storage-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(
                dataSource, VendorDatabasePropertiesLoader.load("h2"), PREFIX, true, null),
            "8.11.0")
        .initialize();

    repository = new JdbcLogBlockRepository(dataSource, PREFIX, 1);
    repository.claimTerm(TERM);
    storage =
        RdbmsLogStorage.writable("test-writer", repository, TERM, WriterConfig.DEFAULT, e -> {});
  }

  @AfterEach
  void tearDown() {
    storage.close();
  }

  @Test
  void shouldCommitAppendedBlocksToTheDatabase() {
    // given
    final List<Long> committed = new CopyOnWriteArrayList<>();
    final AppendListener listener =
        new AppendListener() {
          @Override
          public void onCommit(final long index, final long highestPosition) {
            committed.add(highestPosition);
          }
        };

    // when
    storage.append(1, 3, data("first"), listener);
    storage.append(4, 6, data("second"), listener);

    // then
    await().until(() -> committed.size() == 2);
    assertThat(committed).containsExactly(3L, 6L);
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(block -> new String(block.data(), StandardCharsets.UTF_8))
        .containsExactly("first", "second");
  }

  private static DirectBufferWriter data(final String value) {
    return new DirectBufferWriter(new UnsafeBuffer(value.getBytes(StandardCharsets.UTF_8)));
  }
}
