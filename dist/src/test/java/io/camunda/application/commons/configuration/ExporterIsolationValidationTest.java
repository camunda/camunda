/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beanoverrides.ExporterIsolationValidation;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Lives in {@code dist} rather than {@code configuration} because the collision check needs the
 * real {@code ElasticsearchExporterConfigMerger}/{@code OpensearchExporterConfigMerger} (registered
 * via {@code META-INF/services} in the respective exporter modules) on the classpath to recognize
 * the generic exporters' classes, and {@code configuration} does not depend on those modules.
 */
class ExporterIsolationValidationTest {

  private static final String SECONDARY_STORAGE_TYPE_ES =
      "camunda.data.secondary-storage.type=elasticsearch";
  private static final String SECONDARY_STORAGE_URL_ES =
      "camunda.data.secondary-storage.elasticsearch.url=http://localhost:9200";
  private static final String SECONDARY_STORAGE_PREFIX_ES =
      "camunda.data.secondary-storage.elasticsearch.index-prefix=zeebe-record";
  private static final String SECONDARY_STORAGE_TYPE_OS =
      "camunda.data.secondary-storage.type=opensearch";
  private static final String SECONDARY_STORAGE_URL_OS =
      "camunda.data.secondary-storage.opensearch.url=http://localhost:9200";
  private static final String SECONDARY_STORAGE_PREFIX_OS =
      "camunda.data.secondary-storage.opensearch.index-prefix=zeebe-record";

  private static final ApplicationContextRunner RUNNER =
      new ApplicationContextRunner()
          .withInitializer(context -> context.getEnvironment().setActiveProfiles("broker"))
          .withUserConfiguration(
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class,
              BrokerBasedPropertiesOverride.class);

  private static List<LogEvent> runAndCaptureLogs(final ApplicationContextRunner runner) {
    final var loggerName = ExporterIsolationValidation.class.getName();
    final var appender = new ListAppender("exporter-isolation-validation-appender");
    appender.start();
    final var context = (LoggerContext) LogManager.getContext(false);
    final var loggerConfig = new LoggerConfig(loggerName, Level.ALL, true);
    loggerConfig.addAppender(appender, null, null);
    context.getConfiguration().addLogger(loggerName, loggerConfig);
    context.updateLoggers();

    try {
      runner.run(ctx -> assertThat(ctx).hasNotFailed());
      return appender.getEvents();
    } finally {
      context.getConfiguration().removeLogger(loggerName);
      context.updateLoggers();
      appender.stop();
    }
  }

  private static void assertLoggedWarningContains(
      final List<LogEvent> logEvents, final String... substrings) {
    assertThat(logEvents)
        .extracting(event -> event.getMessage().getFormattedMessage())
        .anySatisfy(
            message -> {
              for (final var substring : substrings) {
                assertThat(message).contains(substring);
              }
            });
  }

  @Nested
  class Legacy {

    @Test
    void shouldWarnWhenElasticsearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "zeebe.broker.exporters.elasticsearch.class-name="
                      + "io.camunda.zeebe.exporter.ElasticsearchExporter",
                  "zeebe.broker.exporters.elasticsearch.args.url=http://localhost:9200",
                  "zeebe.broker.exporters.elasticsearch.args.index.prefix=zeebe-record",
                  SECONDARY_STORAGE_TYPE_ES,
                  SECONDARY_STORAGE_URL_ES,
                  SECONDARY_STORAGE_PREFIX_ES));

      // then
      assertLoggedWarningContains(logEvents, "elasticsearch", "camundaexporter");
    }

    @Test
    void shouldWarnWhenOpensearchExporterSharesPrefixWithSecondaryStorage() {
      // given the same repro, with the legacy OpenSearch exporter instead of Elasticsearch
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "zeebe.broker.exporters.opensearch.class-name="
                      + "io.camunda.zeebe.exporter.opensearch.OpensearchExporter",
                  "zeebe.broker.exporters.opensearch.args.url=http://localhost:9200",
                  "zeebe.broker.exporters.opensearch.args.index.prefix=zeebe-record",
                  SECONDARY_STORAGE_TYPE_OS,
                  SECONDARY_STORAGE_URL_OS,
                  SECONDARY_STORAGE_PREFIX_OS));

      // then
      assertLoggedWarningContains(logEvents, "opensearch", "camundaexporter");
    }

    @Test
    void shouldStartUpWhenPrefixesDiffer() {
      // given the two exporters point at distinct prefixes on the same cluster
      RUNNER
          .withPropertyValues(
              "zeebe.broker.exporters.elasticsearch.class-name="
                  + "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "zeebe.broker.exporters.elasticsearch.args.url=http://localhost:9200",
              "zeebe.broker.exporters.elasticsearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_ES,
              SECONDARY_STORAGE_URL_ES,
              "camunda.data.secondary-storage.elasticsearch.index-prefix=operate-record")
          .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldStartUpWhenPrefixesMatchButUrlsDiffer() {
      // given the same index prefix, but the legacy exporter points at a different cluster than
      // secondary storage — same prefix on different clusters is not a collision
      RUNNER
          .withPropertyValues(
              "zeebe.broker.exporters.elasticsearch.class-name="
                  + "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "zeebe.broker.exporters.elasticsearch.args.url=http://other-cluster:9200",
              "zeebe.broker.exporters.elasticsearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_ES,
              SECONDARY_STORAGE_URL_ES,
              SECONDARY_STORAGE_PREFIX_ES)
          .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldWarnWhenElasticsearchExporterSharesLifecyclePolicyWithSecondaryStorage() {
      // given distinct index prefixes, but the legacy exporter's retention policy is
      // (mis)configured to reuse the secondary storage's own default policy name
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "zeebe.broker.exporters.elasticsearch.class-name="
                      + "io.camunda.zeebe.exporter.ElasticsearchExporter",
                  "zeebe.broker.exporters.elasticsearch.args.url=http://localhost:9200",
                  "zeebe.broker.exporters.elasticsearch.args.index.prefix=zeebe-record",
                  "zeebe.broker.exporters.elasticsearch.args.retention.enabled=true",
                  "zeebe.broker.exporters.elasticsearch.args.retention.policy-name="
                      + "camunda-retention-policy",
                  SECONDARY_STORAGE_TYPE_ES,
                  SECONDARY_STORAGE_URL_ES,
                  "camunda.data.secondary-storage.elasticsearch.index-prefix=operate-record",
                  "camunda.data.secondary-storage.retention.enabled=true"));

      // then
      assertLoggedWarningContains(logEvents, "elasticsearch", "secondary-storage");
    }
  }

  @Nested
  class UnifiedConfig {

    @Test
    void shouldWarnWhenElasticsearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "camunda.data.exporters.elasticsearch.class-name="
                      + "io.camunda.zeebe.exporter.ElasticsearchExporter",
                  "camunda.data.exporters.elasticsearch.args.url=http://localhost:9200",
                  "camunda.data.exporters.elasticsearch.args.index.prefix=zeebe-record",
                  SECONDARY_STORAGE_TYPE_ES,
                  SECONDARY_STORAGE_URL_ES,
                  SECONDARY_STORAGE_PREFIX_ES));

      // then
      assertLoggedWarningContains(logEvents, "elasticsearch", "camundaexporter");
    }

    @Test
    void shouldWarnWhenOpensearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "camunda.data.exporters.opensearch.class-name="
                      + "io.camunda.zeebe.exporter.opensearch.OpensearchExporter",
                  "camunda.data.exporters.opensearch.args.url=http://localhost:9200",
                  "camunda.data.exporters.opensearch.args.index.prefix=zeebe-record",
                  SECONDARY_STORAGE_TYPE_OS,
                  SECONDARY_STORAGE_URL_OS,
                  SECONDARY_STORAGE_PREFIX_OS));

      // then
      assertLoggedWarningContains(logEvents, "opensearch", "camundaexporter");
    }

    @Test
    void shouldStartUpWhenPrefixesDiffer() {
      // given the two exporters point at distinct prefixes on the same cluster
      RUNNER
          .withPropertyValues(
              "camunda.data.exporters.elasticsearch.class-name="
                  + "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "camunda.data.exporters.elasticsearch.args.url=http://localhost:9200",
              "camunda.data.exporters.elasticsearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_ES,
              SECONDARY_STORAGE_URL_ES,
              "camunda.data.secondary-storage.elasticsearch.index-prefix=operate-record")
          .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldWarnWhenElasticsearchExporterSharesLifecyclePolicyWithSecondaryStorage() {
      // given distinct index prefixes, but the generic exporter's retention policy is
      // (mis)configured to reuse the secondary storage's own default policy name
      final var logEvents =
          runAndCaptureLogs(
              RUNNER.withPropertyValues(
                  "camunda.data.exporters.elasticsearch.class-name="
                      + "io.camunda.zeebe.exporter.ElasticsearchExporter",
                  "camunda.data.exporters.elasticsearch.args.url=http://localhost:9200",
                  "camunda.data.exporters.elasticsearch.args.index.prefix=zeebe-record",
                  "camunda.data.exporters.elasticsearch.args.retention.enabled=true",
                  "camunda.data.exporters.elasticsearch.args.retention.policy-name="
                      + "camunda-retention-policy",
                  SECONDARY_STORAGE_TYPE_ES,
                  SECONDARY_STORAGE_URL_ES,
                  "camunda.data.secondary-storage.elasticsearch.index-prefix=operate-record",
                  "camunda.data.secondary-storage.retention.enabled=true"));

      // then
      assertLoggedWarningContains(logEvents, "elasticsearch", "secondary-storage");
    }
  }
}
