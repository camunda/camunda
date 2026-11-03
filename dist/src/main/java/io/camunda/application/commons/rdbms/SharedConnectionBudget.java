/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.jspecify.annotations.NullMarked;

/**
 * Proof of concept for P5: the connections of one pool shared by several physical tenants, each
 * with a guaranteed minimum and a maximum.
 *
 * <p>The pool holds the sum of the guarantees plus a shared burst. A tenant first takes one of its
 * own guaranteed connections; once those are in use, it competes for the shared burst, up to its
 * maximum. A busy tenant can take the whole burst, but never another tenant's guarantee, so the
 * other tenants still get a connection.
 *
 * <p>The pool is any data source. Over one pool of one database and user, it is a shared pool. Over
 * each tenant's own pool, it is a cap on the connections a node uses across its tenants.
 */
@NullMarked
final class SharedConnectionBudget {

  private final Semaphore burst;
  private final int poolSize;
  private final Map<String, TenantShare> shares;

  private SharedConnectionBudget(
      final int burstSize, final int poolSize, final Map<String, TenantShare> shares) {
    burst = new Semaphore(burstSize, true);
    this.poolSize = poolSize;
    this.shares = shares;
  }

  /**
   * @param limits the guaranteed and maximum connections of each tenant
   * @param burstSize the connections that any tenant may use beyond its guarantee
   */
  static SharedConnectionBudget of(final Map<String, Limits> limits, final int burstSize) {
    final var shares =
        limits.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, e -> new TenantShare(e.getKey(), e.getValue())));
    final int guaranteed = limits.values().stream().mapToInt(Limits::guaranteed).sum();
    return new SharedConnectionBudget(burstSize, guaranteed + burstSize, shares);
  }

  /** The connections the shared pool must be able to hold: every guarantee plus the burst. */
  int poolSize() {
    return poolSize;
  }

  /** The tenant's view of the pool, which hands out connections within the tenant's limits. */
  DataSource dataSourceFor(
      final String physicalTenantId, final DataSource pool, final Duration timeout) {
    final var share = shares.get(physicalTenantId);
    if (share == null) {
      throw new IllegalArgumentException("No connection budget for tenant " + physicalTenantId);
    }
    return new BudgetedDataSource(pool, () -> share.acquire(burst, timeout));
  }

  int inUse(final String physicalTenantId) {
    final var share = shares.get(physicalTenantId);
    return share == null ? 0 : share.inUse();
  }

  record Limits(int guaranteed, int maximum) {
    Limits {
      if (guaranteed < 0 || maximum < guaranteed || maximum < 1) {
        throw new IllegalArgumentException(
            "Expected 0 <= guaranteed <= maximum and maximum >= 1, got %d and %d"
                .formatted(guaranteed, maximum));
      }
    }
  }

  /** Gives one permit back when its connection closes. */
  @FunctionalInterface
  interface Permit {
    void release();
  }

  @FunctionalInterface
  interface PermitSource {
    Permit acquire() throws SQLException;
  }

  private static final class TenantShare {
    private final String physicalTenantId;
    private final Semaphore guaranteed;
    private final Semaphore maximum;
    private final AtomicInteger held = new AtomicInteger();

    private TenantShare(final String physicalTenantId, final Limits limits) {
      this.physicalTenantId = physicalTenantId;
      guaranteed = new Semaphore(limits.guaranteed(), true);
      maximum = new Semaphore(limits.maximum(), true);
    }

    private Permit acquire(final Semaphore burst, final Duration timeout) throws SQLException {
      final long deadline = System.nanoTime() + timeout.toNanos();
      try {
        if (!maximum.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
          throw exhausted("its maximum");
        }
        while (true) {
          if (guaranteed.tryAcquire()) {
            return held(guaranteed);
          }
          if (burst.tryAcquire(10, TimeUnit.MILLISECONDS)) {
            return held(burst);
          }
          if (System.nanoTime() > deadline) {
            maximum.release();
            throw exhausted("the shared burst");
          }
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SQLTransientConnectionException("Interrupted waiting for a connection", e);
      }
    }

    private int inUse() {
      return held.get();
    }

    private SQLException exhausted(final String what) {
      return new SQLTransientConnectionException(
          "Physical tenant '%s' has no connection left within %s"
              .formatted(physicalTenantId, what));
    }

    private Permit held(final Semaphore from) {
      held.incrementAndGet();
      final var released = new AtomicBoolean();
      return () -> {
        if (released.compareAndSet(false, true)) {
          held.decrementAndGet();
          from.release();
          maximum.release();
        }
      };
    }
  }

  private record BudgetedDataSource(DataSource pool, PermitSource permits) implements DataSource {

    @Override
    public Connection getConnection() throws SQLException {
      final var permit = permits.acquire();
      try {
        return releasingOnClose(pool.getConnection(), permit);
      } catch (final SQLException | RuntimeException e) {
        permit.release();
        throw e;
      }
    }

    @Override
    public Connection getConnection(final String username, final String password)
        throws SQLException {
      final var permit = permits.acquire();
      try {
        return releasingOnClose(pool.getConnection(username, password), permit);
      } catch (final SQLException | RuntimeException e) {
        permit.release();
        throw e;
      }
    }

    private static Connection releasingOnClose(final Connection connection, final Permit permit) {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                try {
                  return method.invoke(connection, args);
                } catch (final InvocationTargetException e) {
                  throw e.getCause();
                } finally {
                  if (method.getName().equals("close")) {
                    permit.release();
                  }
                }
              });
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
      return pool.getLogWriter();
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
      pool.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
      pool.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
      return pool.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
      return pool.getParentLogger();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
      return pool.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
      return pool.isWrapperFor(iface);
    }
  }
}
