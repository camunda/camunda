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
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.tasks.BackgroundTaskIT;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;

public abstract class ArchiverJobIT<T extends ArchiverJob<?>> extends BackgroundTaskIT<T> {
  private LifecyclePolicyNameVerifier lifecyclePolicyNameVerifier;

  @Override
  @BeforeEach
  protected void setup() {
    super.setup();
    lifecyclePolicyNameVerifier = new LifecyclePolicyNameVerifier();
  }

  @Override
  protected T createBackgroundTask(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var repository = createArchiverRepository(config, resourceProvider);
    return createArchiveJob(config, resourceProvider, repository);
  }

  @Override
  protected void updateConfig(final ExporterConfiguration config) {
    config.getHistory().getRetention().setEnabled(true);
    config.getHistory().getRetention().setPolicyName(testPrefix + "-camunda-retention-policy");
  }

  protected <E extends ExporterEntity<E>> E create(final Supplier<E> constructor) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var entity = constructor.get();
    entity.setId(String.valueOf(id));
    return entity;
  }

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity,
      final String datedIndexSuffix)
      throws IOException {
    verifyMoved(templateDescriptor, client, entity, parent.getId(), datedIndexSuffix);
  }

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String datedIndexSuffix)
      throws IOException {
    verifyMoved(templateDescriptor, client, entity, (String) null, datedIndexSuffix);
  }

  protected void verifyMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing,
      final String datedIndexSuffix)
      throws IOException {
    // should no longer be in the original index
    final var originalIndexEntity =
        client.get(
            entity.getId(), routing, templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity)
        .describedAs(
            "Expected %s to have been deleted from %s",
            entity, templateDescriptor.getFullQualifiedName())
        .isNull();

    // should now be in the dated index
    final var dateIndex = templateDescriptor.getFullQualifiedName() + datedIndexSuffix;
    final var newIndexEntity = client.get(entity.getId(), routing, dateIndex, entity.getClass());
    assertThat(newIndexEntity)
        .describedAs("Expected %s to have been moved to %s", entity, dateIndex)
        .isEqualTo(entity);

    lifecyclePolicyNameVerifier.verifyIndexHasLifecyclePolicy(
        client, dateIndex, getExpectedLifecyclePolicyName());
  }

  protected void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyNotMoved(templateDescriptor, client, entity, (String) null);
  }

  protected void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> entity)
      throws IOException {
    verifyNotMoved(templateDescriptor, client, entity, parent.getId());
  }

  private void verifyNotMoved(
      final IndexTemplateDescriptor templateDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity,
      final String routing)
      throws IOException {
    final var originalIndexEntity =
        client.get(
            entity.getId(), routing, templateDescriptor.getFullQualifiedName(), entity.getClass());
    assertThat(originalIndexEntity)
        .describedAs(
            "Expected %s to still be in %s", entity, templateDescriptor.getFullQualifiedName())
        .isEqualTo(entity);
  }

  protected String getExpectedLifecyclePolicyName() {
    return testPrefix + "-camunda-retention-policy";
  }

  private ArchiverRepository createArchiverRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              createAsyncESClient(config),
              executor,
              exporterMetrics,
              LOGGER));
    } else {
      final var asyncClient = createOSAsyncClient(config);
      final var genericClient =
          new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions());

      return closeLater(
          new OpenSearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              asyncClient,
              genericClient,
              executor,
              exporterMetrics,
              LOGGER));
    }
  }

  abstract T createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository);

  static class LifecyclePolicyNameVerifier {
    private final Set<String> alreadyVerifiedIndexes = new HashSet<>();

    void verifyIndexHasLifecyclePolicy(
        final SearchClientAdapter client, final String indexName, final String expectedPolicy) {
      // no need to check the same index over and over (as we're checking per-doc)
      if (alreadyVerifiedIndexes.contains(indexName)) {
        return;
      }
      Awaitility.await()
          .atMost(EXECUTE_TIMEOUT)
          .untilAsserted(
              () -> {
                final var policy = client.getLifecyclePolicyNameForIndex(indexName);
                assertThat(policy).isEqualTo(expectedPolicy);
              });
      alreadyVerifiedIndexes.add(indexName);
    }
  }
}
