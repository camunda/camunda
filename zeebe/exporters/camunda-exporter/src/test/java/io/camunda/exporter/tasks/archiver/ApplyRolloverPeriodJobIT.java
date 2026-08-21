/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.tasks.BackgroundTaskIT;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;

public class ApplyRolloverPeriodJobIT extends BackgroundTaskIT<ApplyRolloverPeriodJob> {

  private static final String DATE_SUFFIX = "2024-01-15";
  private static final String ORDINAL_SUFFIX = TargetIndex.ORDINAL_SUFFIX_START + "12345";

  @Override
  protected ApplyRolloverPeriodJob createBackgroundTask(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var repository = createArchiverRepository(config, resourceProvider);
    return new ApplyRolloverPeriodJob(repository, LOGGER);
  }

  @Override
  protected void updateConfig(final ExporterConfiguration config) {
    config.getHistory().getRetention().setEnabled(true);
    config.getHistory().getRetention().setPolicyName(testPrefix + "-camunda-retention-policy");
  }

  @TestTemplate
  void shouldApplyLifecyclePolicyToDatedIndexes(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var datedIndexName = listViewTemplate.getFullQualifiedName() + DATE_SUFFIX;
          client.createIndex(datedIndexName, 0);

          // when
          final var result = job.execute();

          // then
          assertThat(result).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);

          Awaitility.await()
              .atMost(EXECUTE_TIMEOUT)
              .untilAsserted(
                  () -> {
                    final var policy = client.getLifecyclePolicyNameForIndex(datedIndexName);
                    assertThat(policy).isEqualTo(testPrefix + "-camunda-retention-policy");
                  });
        });
  }

  @TestTemplate
  void shouldNotApplyLifecyclePolicyToMainIndex(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var mainIndexName = listViewTemplate.getFullQualifiedName();

          // when
          final var result = job.execute();

          // then
          assertThat(result).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);

          final var policy = client.getLifecyclePolicyNameForIndex(mainIndexName);
          assertThat(policy).isNull();
        });
  }

  @TestTemplate
  void shouldNotApplyLifecyclePolicyToOrdinalIndex(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var ordinalIndexName = listViewTemplate.getFullQualifiedName() + ORDINAL_SUFFIX;
          client.createIndex(ordinalIndexName, 0);

          // when
          final var result = job.execute();

          // then
          assertThat(result).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);

          final var policy = client.getLifecyclePolicyNameForIndex(ordinalIndexName);
          assertThat(policy).isNull();
        });
  }

  @TestTemplate
  void shouldNotApplyLifecyclePolicyWhenRetentionDisabled(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var listViewTemplate =
              resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
          final var datedIndexName = listViewTemplate.getFullQualifiedName() + DATE_SUFFIX;
          client.createIndex(datedIndexName, 0);
          config.getHistory().getRetention().setEnabled(false);

          // when
          final var result = job.execute();

          // then
          assertThat(result).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);

          final var policy = client.getLifecyclePolicyNameForIndex(datedIndexName);
          assertThat(policy).isNull();
        });
  }
}
