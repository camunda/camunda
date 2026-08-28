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
import io.camunda.zeebe.util.HashUtil;
import io.camunda.zeebe.util.VersionUtil;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    final var valueType = ValueType.VARIABLE;
    final var record =
        recordFactory.generateRecord(
            b ->
                b.withValueType(valueType)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var index = router.indexFor(record);

    // then
    assertThat(index)
        .isEqualTo("foo-bar_variable_" + VersionUtil.getVersionLowerCase() + "_2022-04-01");
  }

  @Test
  void shouldReturnIndexWithHourSuffixForRecord() {
    // given
    config.prefix = "foo-bar";
    config.indexSuffixDatePattern = "yyyy-MM-dd_HH";
    final var router = new RecordIndexRouter(config);
    final var timestamp = Instant.parse("2022-04-01T13:00:00Z");
    final var valueType = ValueType.VARIABLE;
    final var record =
        recordFactory.generateRecord(
            b ->
                b.withValueType(valueType)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var index = router.indexFor(record);

    // then
    assertThat(index)
        .isEqualTo("foo-bar_variable_" + VersionUtil.getVersionLowerCase() + "_2022-04-01_13");
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
  void shouldReturnIndexPrefixForValueType() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.PROCESS;

    // when
    final var prefix = router.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());

    // then
    assertThat(prefix).isEqualTo("foo-bar_process_" + VersionUtil.getVersionLowerCase());
  }

  @Test
  void shouldReturnIndexPrefixForValueTypeWithUnderscores() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.PROCESS_MESSAGE_SUBSCRIPTION;

    // when
    final var prefix = router.indexPrefixForValueType(valueType, VersionUtil.getVersionLowerCase());

    // then
    assertThat(prefix)
        .isEqualTo("foo-bar_process-message-subscription_" + VersionUtil.getVersionLowerCase());
  }

  @Test
  void shouldReturnSearchPatternForValueTypeWithUnderscores() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.PROCESS_MESSAGE_SUBSCRIPTION;

    // when
    final var prefix =
        router.searchPatternForValueType(valueType, VersionUtil.getVersionLowerCase());

    // then
    assertThat(prefix)
        .isEqualTo(
            "foo-bar_process-message-subscription_" + VersionUtil.getVersionLowerCase() + "_*");
  }

  @Test
  void shouldReturnSearchPatternForValueType() {
    // given
    config.prefix = "foo-bar";
    final var valueType = ValueType.PROCESS;
    final var version = "8.6.0";

    // when
    final var prefix = router.searchPatternForValueType(valueType, version);

    // then
    assertThat(prefix).isEqualTo("foo-bar_process_" + version + "_*");
  }

  @Test
  void shouldUseBalancedRoutingForRecordOfCurrentVersion() {
    // given
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(
            b -> b.withPartitionId(3).withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var routing = router.routingFor(record);

    // then - partition 3 occupies the third shard, rather than whichever shard the bare partition
    // id happens to hash to
    assertThat(HashUtil.getShardForRouting(routing, 3)).isEqualTo(2);
  }

  @Test
  void shouldGiveEveryShardAPartition() {
    // given - as many partitions as shards
    config.setNumberOfShards(3);

    // when
    final var shards =
        IntStream.rangeClosed(1, 3)
            .map(partitionId -> HashUtil.getShardForRouting(routingFor(partitionId), 3))
            .boxed()
            .toList();

    // then - no shard is left empty while another holds two partitions, which is what routing by
    // the bare partition id did
    assertThat(shards).containsExactlyInAnyOrder(0, 1, 2);
  }

  @Test
  void shouldSpreadMorePartitionsThanShardsEvenly() {
    // given
    config.setNumberOfShards(3);

    // when
    final var partitionsPerShard =
        IntStream.rangeClosed(1, 9)
            .boxed()
            .collect(
                Collectors.groupingBy(
                    partitionId -> HashUtil.getShardForRouting(routingFor(partitionId), 3),
                    Collectors.counting()));

    // then
    assertThat(partitionsPerShard).containsOnlyKeys(0, 1, 2).containsValues(3L, 3L, 3L);
  }

  @Test
  void shouldRouteEveryRecordOfAPartitionToTheSameShard() {
    // given
    config.setNumberOfShards(3);
    final var version = VersionUtil.getVersionLowerCase();
    final var first =
        recordFactory.generateRecord(
            b -> b.withPartitionId(2).withPosition(1).withBrokerVersion(version));
    final var second =
        recordFactory.generateRecord(
            b -> b.withPartitionId(2).withPosition(9999).withBrokerVersion(version));

    // when
    final var firstRouting = router.routingFor(first);
    final var secondRouting = router.routingFor(second);

    // then - a reader paging through a partition by position would otherwise depend on the refresh
    // timing of several shards to see the records in order
    assertThat(firstRouting).isEqualTo(secondRouting);
  }

  @Test
  void shouldKeepTheRoutingValueStableAcrossVersions() {
    // given
    config.setNumberOfShards(3);

    // then - pinned because changing the value of an existing partition moves its documents to
    // another shard, so re-exporting a record would add a copy instead of overwriting it
    assertThat(routingFor(1)).isEqualTo("1#3");
    assertThat(routingFor(2)).isEqualTo("2#3");
    assertThat(routingFor(3)).isEqualTo("3#1");
  }

  @Test
  void shouldRouteByPartitionIdWhenTheNumberOfShardsIsUnset() {
    // given - the exporter does not configure the setting, leaving the index on the default of one
    // shard, where there is nothing to balance
    config.setNumberOfShards(null);
    final var record =
        recordFactory.generateRecord(
            b -> b.withPartitionId(3).withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }

  @Test
  void shouldRouteByPartitionIdForASingleShard() {
    // given
    config.setNumberOfShards(1);
    final var record =
        recordFactory.generateRecord(
            b -> b.withPartitionId(3).withBrokerVersion(VersionUtil.getVersionLowerCase()));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }

  @Test
  void shouldUseBalancedRoutingForPreReleaseOfVersionIntroducingIt() {
    // given
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("8.11.0-alpha1"));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3#1");
  }

  @Test
  void shouldUseBalancedRoutingForFirstVersionOfBackportedLine() {
    // given
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("8.9.18"));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3#1");
  }

  @Test
  void shouldUseBalancedRoutingForLineNewerThanAnyKnownOne() {
    // given - a record written by a newer broker, as happens when a broker re-exports records
    // another broker has already written during an upgrade
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("8.12.0"));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3#1");
  }

  @Test
  void shouldRouteByPartitionIdForLastVersionOfLineBeforeBalancedRouting() {
    // given - a record written before balanced routing reached its line, which is indexed into
    // that version's index and must keep that index's routing scheme
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("8.9.17"));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }

  @Test
  void shouldRouteByPartitionIdForLineThatNeverGotBalancedRouting() {
    // given
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("8.7.40"));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }

  @Test
  void shouldRouteByPartitionIdForUnparseableBrokerVersion() {
    // given
    config.setNumberOfShards(3);
    final var record =
        recordFactory.generateRecord(b -> b.withPartitionId(3).withBrokerVersion("not-a-version"));

    // when
    final var routing = router.routingFor(record);

    // then - without a version to compare, the legacy routing is the safe choice
    assertThat(routing).isEqualTo("3");
  }

  private String routingFor(final int partitionId) {
    return router.routingFor(
        recordFactory.generateRecord(
            b ->
                b.withPartitionId(partitionId)
                    .withBrokerVersion(VersionUtil.getVersionLowerCase())));
  }
}
