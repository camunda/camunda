/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration.IndexConfiguration;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * While these tests are generally testing simple methods, I think their value is mostly in ensuring
 * our API remains stable. Consumers of the exported documents expect indices, IDs, etc., to have
 * stable names/values, so any changes should require one to think twice about this.
 *
 * <p>This isn't a cure-all, but it should at least raise a flag when one of these fails.
 */
@Execution(ExecutionMode.CONCURRENT)
final class RecordIndexRouterTest {
  private final ProtocolFactory recordFactory = new ProtocolFactory();
  private final IndexConfiguration config = new IndexConfiguration();
  private final RecordIndexRouter router = new RecordIndexRouter(config);

  @Test
  void shouldReturnIndexForRecord() {
    // given
    config.prefix = "foo-bar";
    final var timestamp = Instant.parse("2022-04-01T00:00:00Z");
    final var record =
        recordFactory.generateRecord(
            b ->
                b.withValueType(ValueType.VARIABLE)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var index = router.indexFor(record);

    // then
    assertThat(index).isEqualTo("foo-bar_" + VersionUtil.getVersionLowerCase() + "_2022-04-01");
  }

  @Test
  void shouldReturnIndexWithHourSuffixForRecord() {
    // given
    config.prefix = "foo-bar";
    config.indexSuffixDatePattern = "yyyy-MM-dd_HH";
    final var router = new RecordIndexRouter(config);
    final var timestamp = Instant.parse("2022-04-01T13:00:00Z");
    final var record =
        recordFactory.generateRecord(
            b ->
                b.withValueType(ValueType.VARIABLE)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var index = router.indexFor(record);

    // then
    assertThat(index)
        .isEqualTo("foo-bar_" + VersionUtil.getVersionLowerCase() + "_2022-04-01_13");
  }

  @Test
  void shouldReturnSameIndexForDifferentValueTypes() {
    // given - two records with different value types but the same timestamp and version
    config.prefix = "foo-bar";
    final var timestamp = Instant.parse("2022-04-01T00:00:00Z");
    final var version = VersionUtil.getVersionLowerCase();
    final var variableRecord =
        recordFactory.generateRecord(
            b ->
                b.withValueType(ValueType.VARIABLE)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(version));
    final var incidentRecord =
        recordFactory.generateRecord(
            b ->
                b.withValueType(ValueType.INCIDENT)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(version));

    // when
    final var variableIndex = router.indexFor(variableRecord);
    final var incidentIndex = router.indexFor(incidentRecord);

    // then - both records land in the same index regardless of value type
    assertThat(variableIndex).isEqualTo(incidentIndex);
  }

  @Test
  void shouldFailOnInvalidPattern() {
    // given
    config.prefix = "foo-bar";
    config.indexSuffixDatePattern = "yyyyy-21-d~zxqalkd_HH";

    assertThatThrownBy(() -> new RecordIndexRouter(config))
        .hasMessageContaining("Unknown pattern letter: l")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldReturnIdForRecord() {
    // given
    final var record = recordFactory.generateRecord(b -> b.withPosition(1).withPartitionId(32));

    // when
    final var id = router.idFor(record);

    // then
    assertThat(id).isEqualTo("32-1");
  }

  @Test
  void shouldReturnIndexPrefix() {
    // given
    config.prefix = "foo-bar";

    // when
    final var prefix = router.indexPrefix(VersionUtil.getVersionLowerCase());

    // then
    assertThat(prefix).isEqualTo("foo-bar_" + VersionUtil.getVersionLowerCase());
  }

  @Test
  void shouldReturnSearchPattern() {
    // given
    config.prefix = "foo-bar";
    final var version = "8.6.0";

    // when
    final var pattern = router.searchPattern(version);

    // then
    assertThat(pattern).isEqualTo("foo-bar_" + version + "_*");
  }

  @Test
  void shouldReturnAliasName() {
    // given
    config.prefix = "foo-bar";

    // when
    final var alias = router.aliasName();

    // then
    assertThat(alias).isEqualTo("foo-bar");
  }

  @Test
  void shouldReturnPartitionIdAsRoutingFor() {
    // given
    final var record = recordFactory.generateRecord(b -> b.withPartitionId(3));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }
}
