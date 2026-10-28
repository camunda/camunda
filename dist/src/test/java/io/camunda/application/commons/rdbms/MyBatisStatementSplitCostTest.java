/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.data.secondarystorage.Rdbms;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Measures how much of a tenant's MyBatis graph the read statements a gateway needs, and the write
 * statements an exporter needs, account for. It is a measurement, so it only runs with {@code
 * -Dcamunda.pt.cost.harness=true}; the result goes to {@code target/pt-cost}.
 */
@EnabledIfSystemProperty(named = "camunda.pt.cost.harness", matches = "true")
class MyBatisStatementSplitCostTest {

  private static final int GRAPHS = 20;

  @Test
  void shouldMeasureTheCostOfReadAndWriteStatements() throws Exception {
    // given
    final var vendor = VendorDatabasePropertiesLoader.load("postgresql");
    final var sample = PhysicalTenantMyBatis.build(new UnpooledDataSource(rdbms()), vendor, "");
    final List<MappedStatement> statements =
        statements(sample.sqlSessionFactory().getConfiguration());
    final long distinct = statements.size();
    final long reads =
        statements.stream().filter(ms -> ms.getSqlCommandType() == SqlCommandType.SELECT).count();

    // when
    final long full = bytesPerGraph(vendor, configuration -> {});
    final long readOnly =
        bytesPerGraph(
            vendor,
            configuration ->
                removeStatements(
                    configuration, ms -> ms.getSqlCommandType() != SqlCommandType.SELECT));
    final long writeOnly =
        bytesPerGraph(
            vendor,
            configuration -> {
              removeStatements(
                  configuration, ms -> ms.getSqlCommandType() == SqlCommandType.SELECT);
              configuration.getResultMaps().clear();
            });

    // then
    final var report =
        String.format(
            Locale.ROOT,
            """
            # MyBatis graph of one tenant, read and write statements

            | graph | KiB | statements |
            |---|---|---|
            | full | %d | %d |
            | read statements only (gateway) | %d | %d |
            | write statements only, no result maps (exporter) | %d | %d |
            """,
            full / 1024,
            distinct,
            readOnly / 1024,
            reads,
            writeOnly / 1024,
            distinct - reads);
    final var out = Path.of(System.getProperty("camunda.pt.cost.output", "target/pt-cost"));
    Files.createDirectories(out);
    Files.writeString(out.resolve("mybatis-statement-split.md"), report);
    assertThat(readOnly).isLessThan(full);
    assertThat(writeOnly).isLessThan(full);
  }

  private static long bytesPerGraph(
      final VendorDatabaseProperties vendor, final Consumer<Configuration> trim) {
    final long before = stableHeapUsed();
    final List<PhysicalTenantMyBatis> graphs = new ArrayList<>();
    for (int i = 0; i < GRAPHS; i++) {
      final var graph = PhysicalTenantMyBatis.build(new UnpooledDataSource(rdbms()), vendor, "");
      trim.accept(graph.sqlSessionFactory().getConfiguration());
      graphs.add(graph);
    }
    final long after = stableHeapUsed();
    assertThat(graphs).hasSize(GRAPHS);
    return (after - before) / GRAPHS;
  }

  private static List<MappedStatement> statements(final Configuration configuration) {
    final Collection<?> values = configuration.getMappedStatements();
    return values.stream()
        .filter(MappedStatement.class::isInstance)
        .map(MappedStatement.class::cast)
        .distinct()
        .toList();
  }

  private static void removeStatements(
      final Configuration configuration, final Predicate<MappedStatement> filter) {
    final Collection<?> values = configuration.getMappedStatements();
    values.removeIf(value -> value instanceof final MappedStatement ms && filter.test(ms));
  }

  private static long stableHeapUsed() {
    final var previous = new AtomicLong(Long.MAX_VALUE);
    Awaitility.await("heap used is stable across full GCs")
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(500))
        .until(
            () -> {
              ManagementFactory.getMemoryMXBean().gc();
              final long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
              return Math.abs(previous.getAndSet(used) - used) <= used / 200;
            });
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
  }

  private static Rdbms rdbms() {
    final var rdbms = new Rdbms();
    rdbms.setUrl("jdbc:postgresql://localhost:1/camunda");
    return rdbms;
  }
}
