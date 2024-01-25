/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.ColumnFamily48Corrector;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SuppressWarnings("deprecation") // we need to use deprecated column families
public class ColumnFamilyPrefixCorrectionMigrationTest {

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class ColumnFamily48CorrectorTestTest {
    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private ColumnFamily48Corrector sut;

    private ColumnFamily<DbString, DbLong> wrongMessageStatsColumnFamily;
    private DbString messagesDeadlineCountKey;
    private DbLong messagesDeadlineCount;

    private ColumnFamily<DbString, DbLong> correctMessageStatsColumnFamily;

    @BeforeEach
    void setup() {
      sut = new ColumnFamily48Corrector(zeebeDb, transactionContext);

      messagesDeadlineCountKey = new DbString();
      messagesDeadlineCountKey.wrapString(DbMessageState.DEADLINE_MESSAGE_COUNT_KEY);
      messagesDeadlineCount = new DbLong();
      wrongMessageStatsColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION,
              transactionContext,
              new DbString(),
              new DbLong());

      correctMessageStatsColumnFamily =
          zeebeDb.createColumnFamily(
              ZbColumnFamilies.MESSAGE_STATS, transactionContext, new DbString(), new DbLong());
    }

    @Test
    void shouldMoveMessageStatsToCorrectColumnFamily() {
      // given
      messagesDeadlineCount.wrapLong(123);
      wrongMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMessageStatsColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMessageStatsColumnFamily.get(messagesDeadlineCountKey))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L);
    }

    @Test
    void shouldMergeWithCorrectMessageStats() {
      // given
      messagesDeadlineCount.wrapLong(123);
      wrongMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);
      messagesDeadlineCount.wrapLong(456);
      correctMessageStatsColumnFamily.insert(messagesDeadlineCountKey, messagesDeadlineCount);

      // when
      sut.correctColumnFamilyPrefix();

      // then
      Assertions.assertThat(wrongMessageStatsColumnFamily.isEmpty()).isTrue();
      Assertions.assertThat(correctMessageStatsColumnFamily.get(messagesDeadlineCountKey))
          .isNotNull()
          .extracting(DbLong::getValue)
          .isEqualTo(123L + 456L);
    }
  }
}
