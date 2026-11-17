/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.searchEngineClientFromConfig;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.zeebe.util.migration.CurrentSchemaVersion.Kind;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
@ExtendWith(SchemaManagerITInvocationProvider.class)
class ElasticsearchSchemaVersionStoreIT {

  @TestTemplate
  void shouldReportFreshDatabaseWhenMetadataIndexDoesNotYetExist(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given - a real search engine with no metadata index created yet
    try (final var searchEngineClient = searchEngineClientFromConfig(config)) {
      final var store =
          new ElasticsearchSchemaVersionStore(
              searchEngineClient,
              config.connect().getIndexPrefix(),
              config.connect().getTypeEnum().isElasticSearch(),
              "8.10.0");

      // when
      final var currentSchemaVersion = store.getCurrentSchemaVersion();

      // then
      assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.FRESH_DATABASE);
    }
  }

  @TestTemplate
  void shouldReportRealVersionMismatchAgainstStoredSchemaVersion(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given - a real metadata index with an older schema version already recorded, e.g. by a
    // previous minor version's startup
    try (final var searchEngineClient = searchEngineClientFromConfig(config)) {
      final var metadataIndex =
          new MetadataIndex(
              config.connect().getIndexPrefix(), config.connect().getTypeEnum().isElasticSearch());
      new SchemaMetadataStore(
              searchEngineClient, metadataIndex, LoggerFactory.getLogger(getClass()))
          .storeSchemaVersion("8.9.0");

      final var store =
          new ElasticsearchSchemaVersionStore(
              searchEngineClient,
              config.connect().getIndexPrefix(),
              config.connect().getTypeEnum().isElasticSearch(),
              "8.10.0");

      // when - the running application is one minor version ahead of the stored schema
      final var currentSchemaVersion = store.getCurrentSchemaVersion();

      // then - the real mismatch between what's stored and what's running is reported as-is,
      // leaving the upgrade-readiness mapping (MIGRATED vs MIGRATION_IN_PROGRESS) to the caller
      assertThat(currentSchemaVersion.kind()).isEqualTo(Kind.AVAILABLE);
      assertThat(currentSchemaVersion.schemaVersion()).contains("8.9.0");
      assertThat(currentSchemaVersion.stableApplicationVersion()).contains("8.10.0");
    }
  }
}
