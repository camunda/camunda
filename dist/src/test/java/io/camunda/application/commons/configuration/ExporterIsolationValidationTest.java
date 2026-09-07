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
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
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

  @Nested
  class Legacy {

    @Test
    void shouldFailStartupWhenElasticsearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      RUNNER
          .withPropertyValues(
              "zeebe.broker.exporters.elasticsearch.class-name="
                  + "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "zeebe.broker.exporters.elasticsearch.args.url=http://localhost:9200",
              "zeebe.broker.exporters.elasticsearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_ES,
              SECONDARY_STORAGE_URL_ES,
              SECONDARY_STORAGE_PREFIX_ES)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(UnifiedConfigurationException.class)
                    .hasMessageContaining("elasticsearch")
                    .hasMessageContaining("camundaexporter");
              });
    }

    @Test
    void shouldFailStartupWhenOpensearchExporterSharesPrefixWithSecondaryStorage() {
      // given the same repro, with the legacy OpenSearch exporter instead of Elasticsearch
      RUNNER
          .withPropertyValues(
              "zeebe.broker.exporters.opensearch.class-name="
                  + "io.camunda.zeebe.exporter.opensearch.OpensearchExporter",
              "zeebe.broker.exporters.opensearch.args.url=http://localhost:9200",
              "zeebe.broker.exporters.opensearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_OS,
              SECONDARY_STORAGE_URL_OS,
              SECONDARY_STORAGE_PREFIX_OS)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(UnifiedConfigurationException.class)
                    .hasMessageContaining("opensearch")
                    .hasMessageContaining("camundaexporter");
              });
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
  }

  @Nested
  class UnifiedConfig {

    @Test
    void shouldFailStartupWhenElasticsearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      RUNNER
          .withPropertyValues(
              "camunda.data.exporters.elasticsearch.class-name="
                  + "io.camunda.zeebe.exporter.ElasticsearchExporter",
              "camunda.data.exporters.elasticsearch.args.url=http://localhost:9200",
              "camunda.data.exporters.elasticsearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_ES,
              SECONDARY_STORAGE_URL_ES,
              SECONDARY_STORAGE_PREFIX_ES)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(UnifiedConfigurationException.class)
                    .hasMessageContaining("elasticsearch")
                    .hasMessageContaining("camundaexporter");
              });
    }

    @Test
    void shouldFailStartupWhenOpensearchExporterSharesPrefixWithSecondaryStorage() {
      // given
      RUNNER
          .withPropertyValues(
              "camunda.data.exporters.opensearch.class-name="
                  + "io.camunda.zeebe.exporter.opensearch.OpensearchExporter",
              "camunda.data.exporters.opensearch.args.url=http://localhost:9200",
              "camunda.data.exporters.opensearch.args.index.prefix=zeebe-record",
              SECONDARY_STORAGE_TYPE_OS,
              SECONDARY_STORAGE_URL_OS,
              SECONDARY_STORAGE_PREFIX_OS)
          .run(
              context -> {
                // then
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(UnifiedConfigurationException.class)
                    .hasMessageContaining("opensearch")
                    .hasMessageContaining("camundaexporter");
              });
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
  }
}
