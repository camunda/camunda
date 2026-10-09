/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.logging.LogCapturer;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

class RdbmsStatementTraceLogTest {

  private static final String LOGGER_NAME =
      "io.camunda.db.rdbms.sql.MappingRuleMapper.RdbmsStatementTraceLogTest";

  private final RdbmsStatementTraceLog log = new RdbmsStatementTraceLog(LOGGER_NAME);

  @Test
  void debugCallsAreDemotedToTrace() {
    try (LogCapturer logs = LogCapturer.capturing(LOGGER_NAME, Level.TRACE)) {
      log.debug("==>  Preparing: SELECT * FROM MAPPING_RULES");

      // See https://github.com/camunda/camunda/issues/63856: MyBatis's own statement/parameter
      // logging always calls debug(), so this must surface at TRACE, not DEBUG, or a plain
      // DEBUG-level operator config would still be flooded with SQL.
      assertThat(logs.messagesAt(Level.DEBUG)).isEmpty();
      assertThat(logs.messagesAt(Level.TRACE))
          .containsExactly("==>  Preparing: SELECT * FROM MAPPING_RULES");
    }
  }

  @Test
  void traceCallsStayAtTrace() {
    try (LogCapturer logs = LogCapturer.capturing(LOGGER_NAME, Level.TRACE)) {
      log.trace("some trace detail");

      assertThat(logs.messagesAt(Level.TRACE)).containsExactly("some trace detail");
    }
  }

  @Test
  void warnAndErrorAreNotDemoted() {
    try (LogCapturer logs = LogCapturer.capturing(LOGGER_NAME, Level.WARN)) {
      log.warn("a mapper warning");
      log.error("a mapper error");
      log.error("a mapper error with cause", new RuntimeException("boom"));

      assertThat(logs.messagesAt(Level.WARN)).containsExactly("a mapper warning");
      assertThat(logs.messagesAt(Level.ERROR))
          .containsExactly("a mapper error", "a mapper error with cause");
    }
  }

  @Test
  void isDebugEnabledMirrorsUnderlyingTraceLevel() {
    try (LogCapturer ignored = LogCapturer.capturing(LOGGER_NAME, Level.TRACE)) {
      // Once TRACE is enabled for the underlying logger, isDebugEnabled() must also report true -
      // this is what makes MyBatis actually build its (now trace-level) statement-logging
      // proxies. See Log#isDebugEnabled() usage in MyBatis's BaseJdbcLogger.
      assertThat(log.isDebugEnabled()).isTrue();
      assertThat(log.isTraceEnabled()).isTrue();
    }

    try (LogCapturer ignored = LogCapturer.capturing(LOGGER_NAME, Level.DEBUG)) {
      // With only DEBUG enabled on the underlying logger, isDebugEnabled() must report false, so
      // MyBatis skips building the (now-invisible-at-DEBUG) statement-logging proxies entirely.
      assertThat(log.isDebugEnabled()).isFalse();
      assertThat(log.isTraceEnabled()).isFalse();
    }
  }
}
