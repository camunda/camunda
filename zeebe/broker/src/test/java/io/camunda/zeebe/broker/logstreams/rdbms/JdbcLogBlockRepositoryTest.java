/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.db.rdbms.LiquibaseSchemaManager;
import io.camunda.db.rdbms.PerTenantSchemaConfig;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class JdbcLogBlockRepositoryTest {

  private static final String PREFIX = "TEST_";
  private static final int PARTITION_ID = 1;

  private JdbcDataSource dataSource;
  private JdbcLogBlockRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:log-block-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    new LiquibaseSchemaManager(
            new PerTenantSchemaConfig(
                dataSource, VendorDatabasePropertiesLoader.load("h2"), PREFIX, true, null),
            "8.11.0")
        .initialize();

    repository = new JdbcLogBlockRepository(dataSource, PREFIX, PARTITION_ID);
  }

  @Test
  void shouldClaimUnclaimedPartition() {
    // when
    final var claimed = repository.claimTerm(1);

    // then
    assertThat(claimed).isTrue();
  }

  @Test
  void shouldClaimPartitionForHigherTerm() {
    // given
    repository.claimTerm(1);

    // when
    final var claimed = repository.claimTerm(2);

    // then
    assertThat(claimed).isTrue();
  }

  @Test
  void shouldClaimSameTermAgain() {
    // given
    repository.claimTerm(2);

    // when
    final var claimed = repository.claimTerm(2);

    // then
    assertThat(claimed).isTrue();
  }

  @Test
  void shouldNotClaimPartitionForLowerTerm() {
    // given
    repository.claimTerm(2);

    // when
    final var claimed = repository.claimTerm(1);

    // then
    assertThat(claimed).isFalse();
    repository.append(2, List.of(block(1, 1, 2)));
  }

  @Test
  void shouldReadBackAppendedBlocks() {
    // given
    repository.claimTerm(1);
    final var first = block(1, 3, 1);
    final var second = block(4, 6, 1);

    // when
    repository.append(1, List.of(first, second));

    // then
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(first, second);
  }

  @Test
  void shouldRejectAppendFromDeposedLeader() {
    // given
    repository.claimTerm(1);
    repository.append(1, List.of(block(1, 3, 1)));
    repository.claimTerm(2);

    // when / then
    assertThatThrownBy(() -> repository.append(1, List.of(block(4, 6, 1))))
        .isInstanceOf(TermMismatchException.class);
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(LogBlock::lowPosition)
        .containsExactly(1L);
  }

  @Test
  void shouldRejectAppendToUnclaimedPartition() {
    // when / then
    assertThatThrownBy(() -> repository.append(1, List.of(block(1, 3, 1))))
        .isInstanceOf(TermMismatchException.class);
  }

  @Test
  void shouldWriteNoBlockIfOneOfThemExists() {
    // given
    repository.claimTerm(1);
    repository.append(1, List.of(block(4, 6, 1)));

    // when / then
    assertThatThrownBy(() -> repository.append(1, List.of(block(1, 3, 1), block(4, 6, 1))))
        .isInstanceOf(LogBlockRepositoryException.class)
        .isNotInstanceOf(TermMismatchException.class);
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(LogBlock::lowPosition)
        .containsExactly(4L);
  }

  @Test
  void shouldFindBlockContainingPosition() {
    // given
    appendBlocksOfThree(3);

    // when / then
    assertThat(repository.findBlockAtOrBefore(5))
        .get()
        .extracting(LogBlock::lowPosition)
        .isEqualTo(4L);
    assertThat(repository.findBlockAtOrBefore(4))
        .get()
        .extracting(LogBlock::lowPosition)
        .isEqualTo(4L);
  }

  @Test
  void shouldFindLastBlockForPositionPastTheEnd() {
    // given
    appendBlocksOfThree(3);

    // when
    final var block = repository.findBlockAtOrBefore(Long.MAX_VALUE);

    // then
    assertThat(block).get().extracting(LogBlock::lowPosition).isEqualTo(7L);
  }

  @Test
  void shouldFindNoBlockForPositionBeforeTheStart() {
    // given
    appendBlocksOfThree(3);

    // when
    final var block = repository.findBlockAtOrBefore(0);

    // then
    assertThat(block).isEmpty();
  }

  @Test
  void shouldReadBlocksInPages() {
    // given
    appendBlocksOfThree(5);

    // when
    final var firstPage = repository.readBlocksAfter(Long.MIN_VALUE, 2);
    final var secondPage = repository.readBlocksAfter(firstPage.getLast().lowPosition(), 2);
    final var lastPage = repository.readBlocksAfter(secondPage.getLast().lowPosition(), 2);

    // then
    assertThat(firstPage).extracting(LogBlock::lowPosition).containsExactly(1L, 4L);
    assertThat(secondPage).extracting(LogBlock::lowPosition).containsExactly(7L, 10L);
    assertThat(lastPage).extracting(LogBlock::lowPosition).containsExactly(13L);
  }

  @Test
  void shouldKeepPartitionsApart() {
    // given
    final var otherPartition = new JdbcLogBlockRepository(dataSource, PREFIX, PARTITION_ID + 1);
    repository.claimTerm(1);
    otherPartition.claimTerm(5);

    // when
    repository.append(1, List.of(block(1, 3, 1)));
    otherPartition.append(5, List.of(block(1, 2, 5)));

    // then
    assertThat(repository.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(LogBlock::highPosition)
        .containsExactly(3L);
    assertThat(otherPartition.readBlocksAfter(Long.MIN_VALUE, 10))
        .extracting(LogBlock::highPosition)
        .containsExactly(2L);
    assertThatThrownBy(() -> repository.append(5, List.of(block(4, 6, 5))))
        .isInstanceOf(TermMismatchException.class);
  }

  private void appendBlocksOfThree(final int count) {
    repository.claimTerm(1);
    for (int i = 0; i < count; i++) {
      final long low = 1 + 3L * i;
      repository.append(1, List.of(block(low, low + 2, 1)));
    }
  }

  private static LogBlock block(final long low, final long high, final long term) {
    return new LogBlock(low, high, term, ("block " + low + ".." + high).getBytes());
  }
}
