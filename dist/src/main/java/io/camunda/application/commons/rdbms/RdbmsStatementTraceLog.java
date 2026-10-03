/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis {@link Log} implementation that demotes the framework's own per-statement logging from
 * {@code DEBUG} to {@code TRACE}.
 *
 * <p>MyBatis's JDBC logging proxies ({@code PreparedStatementLogger}, {@code ResultSetLogger} and
 * friends) unconditionally emit "==> Preparing:", "==> Parameters:" and "<== Total:" lines for
 * every mapped statement via {@link Log#debug(String)}, with no supported way to change that
 * severity from application configuration. Because every RDBMS-backed Camunda mapper
 * (io.camunda.db.rdbms.sql.*) is invoked through these proxies, simply enabling DEBUG logging
 * anywhere under io.camunda floods the logs with SQL statement/parameter noise - see
 * https://github.com/camunda/camunda/issues/63856.
 *
 * <p>Routing MyBatis's {@code debug()} calls to the underlying SLF4J logger's {@code trace()}
 * instead means operators now need to explicitly enable TRACE for {@code io.camunda.db.rdbms} to
 * see per-statement SQL, while a broader DEBUG - and the mappers' own warnings/errors, which are
 * unaffected - no longer pulls it in. This is installed once, for every RDBMS
 * {@code SqlSessionFactory}, via {@code Configuration#setLogImpl}.
 */
public class RdbmsStatementTraceLog implements Log {

  private final Logger log;

  public RdbmsStatementTraceLog(final String clazz) {
    log = LoggerFactory.getLogger(clazz);
  }

  @Override
  public boolean isDebugEnabled() {
    // MyBatis only pays the cost of wrapping statements/results with a logging proxy when this
    // returns true, so gate it on whether the demoted output would actually be visible.
    return log.isTraceEnabled();
  }

  @Override
  public boolean isTraceEnabled() {
    return log.isTraceEnabled();
  }

  @Override
  public void error(final String s, final Throwable e) {
    log.error(s, e);
  }

  @Override
  public void error(final String s) {
    log.error(s);
  }

  @Override
  public void debug(final String s) {
    // This is the call MyBatis's statement/parameter/result-set logging goes through - demote it.
    log.trace(s);
  }

  @Override
  public void trace(final String s) {
    log.trace(s);
  }

  @Override
  public void warn(final String s) {
    log.warn(s);
  }
}
