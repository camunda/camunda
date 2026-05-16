/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import org.junit.jupiter.api.Test;

class ConfigParserTest {

  @Test
  void shouldParseDefaults() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.maxBatchSize()).isEqualTo(100);
    assertThat(config.maxQueueSize()).isEqualTo(10_000);
    assertThat(config.overflowPolicy()).isEqualTo(OverflowPolicy.BLOCK);
    assertThat(config.flushInterval().toMillis()).isEqualTo(1_000);
    assertThat(config.records().defaults().topic()).isEqualTo("zeebe");
    assertThat(config.records().defaults().allowedTypes()).containsExactly(RecordType.EVENT);
  }

  @Test
  void shouldParseOverflowPolicy() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.overflowPolicy = "drop_newest";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.overflowPolicy()).isEqualTo(OverflowPolicy.DROP_NEWEST);
  }

  @Test
  void shouldParseBlockOverflowPolicy() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.overflowPolicy = "block";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.overflowPolicy()).isEqualTo(OverflowPolicy.BLOCK);
  }

  @Test
  void shouldRejectUnknownOverflowPolicy() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.overflowPolicy = "unknown_policy";

    // when / then
    assertThatThrownBy(() -> ConfigParser.parse(rawConfig))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldParseSpecificRecordOverrides() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.records = new RawRecordsConfig();
    rawConfig.records.job = new RawRecordConfig();
    rawConfig.records.job.topic = "zeebe-job";
    rawConfig.records.job.type = "event,rejection";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.records().forType(ValueType.JOB).topic()).isEqualTo("zeebe-job");
    assertThat(config.records().forType(ValueType.JOB).allowedTypes())
        .containsExactlyInAnyOrder(RecordType.EVENT, RecordType.COMMAND_REJECTION);
  }

  @Test
  void shouldApplyDefaultsWhenAllOptionalFieldsAreNull() {
    // given — only the required servers field is set; everything else uses defaults
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.maxBatchSize()).isEqualTo(100);
    assertThat(config.overflowPolicy()).isEqualTo(OverflowPolicy.BLOCK);
  }

  @Test
  void shouldTreatNullRawConfigLikeEmptyConfig() {
    // given / when — a null rawConfig is treated as an empty config; servers are still required
    assertThatThrownBy(() -> ConfigParser.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("producer.servers");
  }

  @Test
  void shouldParseFlushIntervalMs() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.flushIntervalMs = 500L;

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.flushInterval().toMillis()).isEqualTo(500);
  }

  @Test
  void shouldParseOverflowPolicyCaseInsensitively() {
    // given — user writes "Drop_Oldest" in their YAML
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.overflowPolicy = "Drop_Oldest";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.overflowPolicy()).isEqualTo(OverflowPolicy.DROP_OLDEST);
  }

  @Test
  void shouldParseAdditionalProducerConfig() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.producer.config = "linger.ms=5\nbatch.size=32768";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.producer().additionalConfig())
        .containsEntry("linger.ms", "5")
        .containsEntry("batch.size", "32768");
  }

  @Test
  void shouldInheritDefaultTopicWhenOnlyTypeIsOverridden() {
    // given — only the type is overridden, topic should fall back to default
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.records = new RawRecordsConfig();
    rawConfig.records.incident = new RawRecordConfig();
    rawConfig.records.incident.type = "event,command";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then — topic inherited from defaults
    assertThat(config.records().forType(ValueType.INCIDENT).topic()).isEqualTo("zeebe");
    assertThat(config.records().forType(ValueType.INCIDENT).allowedTypes())
        .containsExactlyInAnyOrder(RecordType.EVENT, RecordType.COMMAND);
  }

  @Test
  void shouldInheritDefaultAllowedTypesWhenOnlyTopicIsOverridden() {
    // given — only topic overridden, allowed types should fall back to default
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.records = new RawRecordsConfig();
    rawConfig.records.variable = new RawRecordConfig();
    rawConfig.records.variable.topic = "zeebe-variable";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then — allowedTypes inherited from defaults (EVENT)
    assertThat(config.records().forType(ValueType.VARIABLE).topic()).isEqualTo("zeebe-variable");
    assertThat(config.records().forType(ValueType.VARIABLE).allowedTypes())
        .containsExactly(RecordType.EVENT);
  }

  @Test
  void shouldRejectMissingBootstrapServers() {
    // given
    final RawConfig rawConfig = new RawConfig();

    // when / then
    assertThatThrownBy(() -> ConfigParser.parse(rawConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("producer.servers");
  }

  @Test
  void shouldRejectNonPositiveQueueSize() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.maxQueueSize = 0;

    // when / then
    assertThatThrownBy(() -> ConfigParser.parse(rawConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxQueueSize");
  }

  @Test
  void shouldRejectNonPositiveBatchSize() {
    // given
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.maxBatchSize = 0;

    // when / then
    assertThatThrownBy(() -> ConfigParser.parse(rawConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxBatchSize");
  }

  @Test
  void shouldParseMultiWordValueTypeOverride() {
    // given — multi-word camelCase field name (processInstance → PROCESS_INSTANCE)
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.records = new RawRecordsConfig();
    rawConfig.records.processInstance = new RawRecordConfig();
    rawConfig.records.processInstance.topic = "zeebe-process-instance";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then — the explicit byValueType() mapping correctly routes processInstance → PROCESS_INSTANCE
    assertThat(config.records().forType(ValueType.PROCESS_INSTANCE).topic())
        .isEqualTo("zeebe-process-instance");
  }

  @Test
  void shouldParseThreeWordValueTypeOverride() {
    // given — three-word camelCase field name (processInstanceCreation → PROCESS_INSTANCE_CREATION)
    final RawConfig rawConfig = new RawConfig();
    rawConfig.producer = new RawProducerConfig();
    rawConfig.producer.servers = "localhost:9092";
    rawConfig.records = new RawRecordsConfig();
    rawConfig.records.processInstanceCreation = new RawRecordConfig();
    rawConfig.records.processInstanceCreation.topic = "zeebe-pic";

    // when
    final Config config = ConfigParser.parse(rawConfig);

    // then
    assertThat(config.records().forType(ValueType.PROCESS_INSTANCE_CREATION).topic())
        .isEqualTo("zeebe-pic");
  }
}
