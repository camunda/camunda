/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.rdbms;

import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * The mailbox and lease stores of a node on one connection pool. Brokers and gateways that use the
 * same database share the rows, which is how they reach each other across regions.
 *
 * <p>The pool should serve only these stores: a lease renewal that waits behind other work for a
 * connection may come too late, and the leader then stops leading although it is healthy.
 */
public final class RdbmsMailboxStores {

  /**
   * How long a lease lasts on this database. Brokers elect with it and gateways judge with it
   * whether a leader is still there, so all nodes of the cluster must use the same.
   */
  public static final Duration LEASE_DURATION = Duration.ofSeconds(10);

  private final DataSource dataSource;
  private final MailboxStore mailboxStore;
  private final LeaseStore leaseStore;

  private RdbmsMailboxStores(final DataSource dataSource, final SqlSessionFactory sessionFactory) {
    this.dataSource = dataSource;
    mailboxStore = new MyBatisMailboxStore(sessionFactory);
    leaseStore = new MyBatisLeaseStore(sessionFactory);
  }

  /**
   * Builds the stores on a pool whose schema was migrated with {@link
   * RdbmsMailboxSchema#CHANGELOG}.
   */
  public static RdbmsMailboxStores create(final DataSource dataSource) {
    return new RdbmsMailboxStores(dataSource, createSessionFactory(dataSource));
  }

  public DataSource dataSource() {
    return dataSource;
  }

  public MailboxStore mailboxStore() {
    return mailboxStore;
  }

  public LeaseStore leaseStore() {
    return leaseStore;
  }

  static SqlSessionFactory createSessionFactory(final DataSource dataSource) {
    final var configuration =
        new Configuration(new Environment("mailbox", new JdbcTransactionFactory(), dataSource));
    for (final var resource : RdbmsMailboxSchema.MAPPERS) {
      try (final var mapperXml =
          RdbmsMailboxStores.class.getClassLoader().getResourceAsStream(resource)) {
        if (mapperXml == null) {
          throw new IllegalStateException("Missing MyBatis mapper resource " + resource);
        }
        new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments())
            .parse();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }
}
