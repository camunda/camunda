/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.startup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.util.unit.DataSize;

final class IdentitySetupOnStartupTest {

  @Test
  @Timeout(unit = TimeUnit.SECONDS, value = 60)
  void shouldInitializeIdentitySetup() {
    try (final LogCapturer logCapturer = new LogCapturer(Loggers.PROCESSOR_LOGGER.getName());
        final TestCluster cluster =
            // Cluster with 3 partitions (to induce command distribution), and reduced sizes for max
            // message size and log segment size, should still be able to setup identity.
            TestCluster.builder()
                .withEmbeddedGateway(true)
                .withBrokersCount(3)
                .withPartitionsCount(3)
                .withReplicationFactor(3)
                .withBrokerConfig(
                    cfg -> {
                      cfg.unifiedConfig()
                          .getCluster()
                          .getNetwork()
                          .setMaxMessageSize(DataSize.ofKilobytes(32));
                      cfg.unifiedConfig().getProcessing().setMaxCommandsInBatch(100);
                    })
                .build()
                .start()
                .awaitCompleteTopology()) {

      Assertions.assertThat(
              RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZED).getFirst())
          .describedAs("Expect that identity setup was completed regardless of small max msg size")
          .isNotNull();

      assertThat(
              logCapturer.contains(
                  "Expected to process commands in a batch, but exceeded the resulting batch size after processing"))
          .describedAs("Expect that the command batch size is not exceeded by identity setup")
          .isFalse();
    }
  }

  static class LogCapturer implements AutoCloseable {
    private final ListAppender appender;

    public LogCapturer(final String loggerName) {
      final LoggerContext context = (LoggerContext) LogManager.getContext(false);
      appender = new ListAppender("TestAppender");
      appender.start();
      context.getConfiguration().getLoggerConfig(loggerName).addAppender(appender, null, null);
    }

    public boolean contains(final String message) {
      return appender.getMessages().stream().anyMatch(msg -> msg.contains(message));
    }

    @Override
    public void close() {
      appender.stop();
      appender.clear();
    }
  }
}
