/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration.IndexConfiguration;
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
    final var valueType = ValueType.VARIABLE;
    final var currentVersion = VersionUtil.getVersionLowerCase();
    final var record =
        recordFactory.generateRecord(
            b ->
                b.withValueType(valueType)
                    .withTimestamp(timestamp.toEpochMilli())
                    .withBrokerVersion(currentVersion));

    // when
    final var index = router.indexFor(record);

    // then
    assertThat(index).isEqualTo("foo-bar_variable_" + currentVersion + "_2022-04-01");
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
  void shouldReturnPartitionIdAsRoutingFor() {
    // given
    final var record = recordFactory.generateRecord(b -> b.withPartitionId(3));

    // when
    final var routing = router.routingFor(record);

    // then
    assertThat(routing).isEqualTo("3");
  }
}
