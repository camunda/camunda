/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * {@link LogBlockRepository} on MyBatis, using the {@link LogBlockMapper} statements of a session
 * factory built by {@link #createSessionFactory}. Every method is one transaction in its own
 * session.
 *
 * <p>An append first reads the partition's term with a row lock, then batch-inserts the blocks,
 * then commits. The row lock serializes appends with {@link #claimTerm}, so a new leader's claim
 * either waits for an in-flight append to finish or makes it fail; a check without the lock would
 * let a deposed leader's append commit after the new leader has read where the log ends.
 */
public final class MyBatisLogBlockRepository implements LogBlockRepository {

  /** SQLSTATE class for integrity constraint violations, e.g. a duplicate primary key. */
  private static final String INTEGRITY_CONSTRAINT_VIOLATION = "23";

  private final SqlSessionFactory sessionFactory;
  private final int partitionId;

  public MyBatisLogBlockRepository(final SqlSessionFactory sessionFactory, final int partitionId) {
    this.sessionFactory = sessionFactory;
    this.partitionId = partitionId;
  }

  /**
   * Builds the factory of the sessions of all partitions' repositories on the given database, which
   * should be a connection pool whose tables were created by {@link
   * RdbmsLogStorageSchema#CHANGELOG} with the same prefix. Build it once per pool.
   */
  public static SqlSessionFactory createSessionFactory(
      final DataSource dataSource, final String tablePrefix) {
    final var configuration =
        new Configuration(new Environment("log-storage", new JdbcTransactionFactory(), dataSource));
    final var variables = new Properties();
    variables.put("prefix", tablePrefix);
    configuration.setVariables(variables);

    final var resource = RdbmsLogStorageSchema.MAPPER;
    try (final var mapperXml =
        MyBatisLogBlockRepository.class.getClassLoader().getResourceAsStream(resource)) {
      if (mapperXml == null) {
        throw new IllegalStateException("Missing MyBatis mapper resource " + resource);
      }
      new XMLMapperBuilder(mapperXml, configuration, resource, configuration.getSqlFragments())
          .parse();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  @Override
  public boolean claimTerm(final long term) {
    try (final var session = sessionFactory.openSession(false)) {
      final var claimed = claimTerm(session, session.getMapper(LogBlockMapper.class), term);
      session.commit(true);
      return claimed;
    } catch (final PersistenceException e) {
      throw new LogBlockRepositoryException(
          "Failed to claim partition %d for term %d".formatted(partitionId, term), e);
    }
  }

  @Override
  public void append(final long term, final List<LogBlock> blocks) {
    if (blocks.isEmpty()) {
      return;
    }

    try (final var session = sessionFactory.openSession(ExecutorType.BATCH, false)) {
      final var mapper = session.getMapper(LogBlockMapper.class);
      // a select runs at once even in a batch session, unlike an update whose count would only be
      // known, and on some drivers not even then, once the batch is flushed
      final var currentTerm = mapper.selectTermForUpdate(partitionId);
      if (currentTerm == null || currentTerm != term) {
        session.rollback(true);
        throw new TermMismatchException(partitionId, term);
      }
      for (final var block : blocks) {
        mapper.insertBlock(
            partitionId, block.lowPosition(), block.highPosition(), block.term(), block.data());
      }
      session.flushStatements();
      session.commit(true);
    } catch (final PersistenceException e) {
      throw new LogBlockRepositoryException(
          "Failed to append %d blocks to partition %d, starting at position %d"
              .formatted(blocks.size(), partitionId, blocks.getFirst().lowPosition()),
          e);
    }
  }

  @Override
  public Optional<LogBlock> findBlockAtOrBefore(final long position) {
    final var blocks =
        read(mapper -> mapper.selectBlocksAtOrBefore(partitionId, position, 1), position);
    return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.getFirst());
  }

  @Override
  public List<LogBlock> readBlocksAfter(final long lowPosition, final int maxBlocks) {
    return read(
        mapper -> mapper.selectBlocksAfter(partitionId, lowPosition, maxBlocks), lowPosition);
  }

  /**
   * Raises the term if it is lower than ours, or inserts it if the partition was never claimed. A
   * peer may insert the row between our failed update and our insert; we then start over and see
   * its term.
   */
  private boolean claimTerm(
      final SqlSession session, final LogBlockMapper mapper, final long term) {
    while (true) {
      if (mapper.raiseTerm(partitionId, term) == 1) {
        return true;
      }

      final var currentTerm = mapper.selectTerm(partitionId);
      if (currentTerm != null) {
        return currentTerm == term;
      }

      try {
        mapper.insertTerm(partitionId, term);
        return true;
      } catch (final PersistenceException e) {
        if (!isIntegrityConstraintViolation(e)) {
          throw e;
        }
        session.rollback(true);
      }
    }
  }

  private List<LogBlock> read(
      final Function<LogBlockMapper, List<LogBlock>> query, final long position) {
    try (final var session = sessionFactory.openSession()) {
      return query.apply(session.getMapper(LogBlockMapper.class));
    } catch (final PersistenceException e) {
      throw new LogBlockRepositoryException(
          "Failed to read blocks of partition %d around position %d"
              .formatted(partitionId, position),
          e);
    }
  }

  private static boolean isIntegrityConstraintViolation(final Throwable error) {
    for (var cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof final SQLException sqlException) {
        final var sqlState = sqlException.getSQLState();
        if (sqlState != null && sqlState.startsWith(INTEGRITY_CONSTRAINT_VIOLATION)) {
          return true;
        }
      }
    }
    return false;
  }
}
