/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@AutoCloseResources
final class CamundaExporterTest {
  private final MockedStatic<ClientAdapter> mockedClientAdapterFactory =
      Mockito.mockStatic(ClientAdapter.class);

  private final ExporterResourceProvider resourceProvider = new DefaultExporterResourceProvider();
  private final ExporterConfiguration configuration = new ExporterConfiguration();
  private final ExporterTestContext testContext =
      new ExporterTestContext()
          .setConfiguration(new ExporterTestConfiguration<>("test", configuration));

  @SuppressWarnings("FieldCanBeLocal")
  @AutoCloseResource
  private CamundaExporter exporter;

  @BeforeEach
  void beforeEach() {
    mockedClientAdapterFactory
        .when(() -> ClientAdapter.of(configuration))
        .thenReturn(new StubClientAdapter());
  }

  private static final class NoopExporterEntityCacheProvider
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName, final XMLUtil xmlUtil) {
      return k -> null;
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return k -> null;
    }
  }

  private static final class StubClientAdapter implements ClientAdapter {
    private final ExporterEntityCacheProvider entityCacheProvider =
        new NoopExporterEntityCacheProvider();
    private final SearchEngineClient client =
        Mockito.mock(
            SearchEngineClient.class,
            Mockito.withSettings().defaultAnswer(Answers.RETURNS_SMART_NULLS));

    @Override
    public SearchEngineClient getSearchEngineClient() {
      return client;
    }

    @Override
    public BatchRequest createBatchRequest() {
      return Mockito.mock(
          BatchRequest.class, Mockito.withSettings().defaultAnswer(Answers.RETURNS_SMART_NULLS));
    }

    @Override
    public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
      return entityCacheProvider;
    }

    @Override
    public void close() {}
  }

  @Nested
  final class OpenTest {
    private final ExporterTestController testController = new ExporterTestController();

    @Test
    void shouldDeserializeMetadataOnOpen() {
      // given
      final var expected = new ExporterMetadata();
      final var metadata = new ExporterMetadata();
      exporter = new CamundaExporter(resourceProvider, metadata);
      expected.setLastIncidentUpdatePosition(3);
      metadata.setLastIncidentUpdatePosition(-1);
      testController.updateLastExportedRecordPosition(-1, expected.serialize());

      // when
      exporter.configure(testContext);
      exporter.open(testController);

      // then
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(3);
    }
  }
}
