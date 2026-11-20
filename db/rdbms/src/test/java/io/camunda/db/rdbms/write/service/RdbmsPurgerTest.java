/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class RdbmsPurgerTest {

  private final SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
  private final SqlSession session = mock(SqlSession.class);
  private final PurgeMapper purgeMapper = mock(PurgeMapper.class);
  private final VendorDatabaseProperties vendorDatabaseProperties =
      mock(VendorDatabaseProperties.class);
  private final RdbmsPurger purger = new RdbmsPurger(sqlSessionFactory, vendorDatabaseProperties);

  @BeforeEach
  void setUp() {
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(PurgeMapper.class)).thenReturn(purgeMapper);
  }

  @Test
  void shouldRunAllStatementsInOneSession() {
    // given
    when(vendorDatabaseProperties.disableFkBeforeTruncate()).thenReturn(true);

    // when
    purger.purgeRdbms();

    // then
    final InOrder inOrder = Mockito.inOrder(sqlSessionFactory, purgeMapper, session);
    inOrder.verify(sqlSessionFactory).openSession();
    inOrder.verify(purgeMapper).disableForeignKeyChecks();
    inOrder.verify(purgeMapper).truncateTable("PROCESS_INSTANCE");
    inOrder.verify(purgeMapper).enableForeignKeyChecks();
    inOrder.verify(session).commit();
    inOrder.verify(session).close();
  }

  @Test
  void shouldEnableForeignKeyChecksWhenTruncateFails() {
    // given
    when(vendorDatabaseProperties.disableFkBeforeTruncate()).thenReturn(true);
    doThrow(new IllegalStateException("lock wait timeout"))
        .when(purgeMapper)
        .truncateTable("PROCESS_INSTANCE");

    // when
    assertThatThrownBy(purger::purgeRdbms).hasMessage("lock wait timeout");

    // then
    verify(purgeMapper).enableForeignKeyChecks();
    verify(session).close();
  }
}
