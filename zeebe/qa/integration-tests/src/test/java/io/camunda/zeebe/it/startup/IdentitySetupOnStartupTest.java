/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.startup;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.util.unit.DataSize;

final class IdentitySetupOnStartupTest {

  private static final LogCapturer LOG_CAPTURER = new LogCapturer();

  /**
   * Cluster with 3 partitions (to induce command distribution), and reduced sizes for max message
   * size and log segment size, should still be able to setup identity.
   */
  @RegisterExtension
  private final ClusteringRuleExtension clusteringRule =
      new ClusteringRuleExtension(
          3,
          3,
          3,
          cfg -> {
            cfg.getExperimental().getFeatures().setEnableIdentitySetup(true);
            cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32));
            cfg.getProcessing().setMaxCommandsInBatch(100);
          });

  @BeforeAll
  static void setup() {
    final Logger logger = (Logger) Loggers.PROCESSOR_LOGGER;
    LOG_CAPTURER.stop();
    LOG_CAPTURER.list.clear();
    LOG_CAPTURER.setContext(logger.getLoggerContext());
    LOG_CAPTURER.start();
    logger.addAppender(LOG_CAPTURER);
  }

  @Test
  @Timeout(unit = TimeUnit.SECONDS, value = 30)
  void shouldInitializeIdentitySetup() {
    Assertions.assertThat(
            RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZED).getFirst())
        .describedAs("Expect that identity setup was completed regardless of small max msg size")
        .isNotNull();

    assertThat(
            LOG_CAPTURER.contains(
                "Expected to process commands in a batch, but exceeded the resulting batch size after processing",
                Level.WARN))
        .describedAs("Expect that the command batch size is not exceeded by identity setup")
        .isFalse();
  }

  static class LogCapturer extends ListAppender<ILoggingEvent> {

    public boolean contains(final String string, final Level level) {
      return list.stream()
          .anyMatch(event -> event.toString().contains(string) && event.getLevel().equals(level));
    }
  }
}
