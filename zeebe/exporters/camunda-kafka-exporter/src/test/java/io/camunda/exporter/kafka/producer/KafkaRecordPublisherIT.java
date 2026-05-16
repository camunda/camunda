/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.exporter.kafka.config.OverflowPolicy;
import io.camunda.exporter.kafka.config.ProducerConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

final class KafkaRecordPublisherIT {

  private static final String TOPIC_NAME = "zeebe-job-it";
  private static final long EXPECTED_LAST_POSITION = 13L;

  @Test
  void shouldPublishRecordsAndPartitionByZeebePartitionId() throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker is required to run KafkaRecordPublisherIT");

    // given
    try (final KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"))) {
      kafka.start();
      createTopic(kafka, TOPIC_NAME, 3);

      final ProducerConfiguration configuration =
          new ProducerConfiguration(
              kafka.getBootstrapServers(), "camunda-kafka-it", 10_000, 10_000, 5_000, Map.of());

      // when — start() spawns the background flush thread which initializes the Kafka producer
      // (including initTransactions()) without blocking the calling thread.
      final List<ConsumerRecord<String, String>> consumed = new ArrayList<>();
      final long lastFlushedPosition;
      try (final KafkaRecordPublisher publisher =
          new KafkaRecordPublisher(
              configuration,
              1,
              100,
              1_000,
              OverflowPolicy.DROP_OLDEST,
              Duration.ofSeconds(1),
              LoggerFactory.getLogger(getClass()))) {
        publisher.start();
        publisher.publish(
            new KafkaExportRecord(TOPIC_NAME, "k1", "v1", Map.of("valueType", "JOB"), 1, 11L));
        publisher.publish(
            new KafkaExportRecord(TOPIC_NAME, "k2", "v2", Map.of("valueType", "JOB"), 2, 12L));
        publisher.publish(
            new KafkaExportRecord(TOPIC_NAME, "k3", "v3", Map.of("valueType", "JOB"), 3, 13L));

        // Wait for the flush thread to init the producer and flush all published records.
        await()
            .atMost(20, TimeUnit.SECONDS)
            .until(() -> publisher.getLastFlushedPosition() >= EXPECTED_LAST_POSITION);

        lastFlushedPosition = publisher.getLastFlushedPosition();
      }

      // then
      assertThat(lastFlushedPosition).isEqualTo(EXPECTED_LAST_POSITION);

      consumeRecords(kafka, TOPIC_NAME, 3, consumed);
      assertThat(consumed).hasSize(3);
      assertThat(consumed)
          .extracting(ConsumerRecord::value)
          .containsExactlyInAnyOrder("v1", "v2", "v3");
      assertThat(consumed).extracting(ConsumerRecord::partition).containsExactlyInAnyOrder(1, 2, 0);
      assertThat(consumed.get(0).headers().lastHeader("valueType")).isNotNull();
    }
  }

  private static void createTopic(
      final KafkaContainer kafka, final String topicName, final int partitions) throws Exception {
    final Properties adminConfig = new Properties();
    adminConfig.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());

    try (final AdminClient adminClient = AdminClient.create(adminConfig)) {
      adminClient.createTopics(List.of(new NewTopic(topicName, partitions, (short) 1))).all().get();
    }
  }

  private static void consumeRecords(
      final KafkaContainer kafka,
      final String topicName,
      final int expectedCount,
      final List<ConsumerRecord<String, String>> consumed) {
    final Map<String, Object> consumerConfig = new HashMap<>();
    consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    consumerConfig.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    try (final KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(consumerConfig, new StringDeserializer(), new StringDeserializer())) {
      consumer.subscribe(List.of(topicName));
      await()
          .atMost(20, TimeUnit.SECONDS)
          .until(
              () -> {
                consumer.poll(Duration.ofMillis(500)).forEach(consumed::add);
                return consumed.size() >= expectedCount;
              });
    }
  }
}
