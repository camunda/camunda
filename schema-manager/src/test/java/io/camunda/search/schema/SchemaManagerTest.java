/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.SchemaMetadataStore.SCHEMA_VERSION_METADATA_ID;
import static io.camunda.webapps.schema.descriptors.index.MetadataIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.MetadataIndex.VALUE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IncompatibleVersionException;
import io.camunda.search.schema.utils.TestIndexDescriptor;
import io.camunda.search.schema.utils.TestTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchemaManagerTest {

  private SearchEngineClient searchEngineClient;
  private SearchEngineConfiguration config;
  private Collection<IndexDescriptor> indexDescriptors;
  private Collection<IndexTemplateDescriptor> templateDescriptors;
  private MetadataIndex metadataIndex;
  private TestIndexDescriptor testIndexDescriptor;
  private TestTemplateDescriptor testTemplateDescriptor;
  private SchemaManager schemaManager;

  @BeforeEach
  void setUp() {
    searchEngineClient = mock(SearchEngineClient.class);

    config = SearchEngineConfiguration.of(c -> c);
    config.schemaManager().setCreateSchema(true);
    config.schemaManager().getRetry().setMaxRetries(1);
    final String indexPrefix = "test";
    config.connect().setIndexPrefix(indexPrefix);

    metadataIndex = new MetadataIndex(indexPrefix, true);
    testIndexDescriptor = new TestIndexDescriptor(indexPrefix, "mappings.json");
    testTemplateDescriptor = new TestTemplateDescriptor(indexPrefix, "mappings.json");
    indexDescriptors = List.of(metadataIndex, testIndexDescriptor);
    templateDescriptors = List.of(testTemplateDescriptor);

    // Mock search engine client operations
    when(searchEngineClient.indexExists(metadataIndex.getFullQualifiedName())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    if (schemaManager != null) {
      schemaManager.close();
    }
  }

  // Test data for forbidden upgrades
  static Stream<Arguments> forbiddenUpgradeScenarios() {
    return Stream.of(
        // Major upgrades are forbidden
        Arguments.of("8.8.0", "9.0.0", "MajorUpgrade"),
        Arguments.of("8.8.0", "9.1.0", "MajorUpgrade"),

        // Skipped minor versions are forbidden
        Arguments.of("8.8.0", "8.10.0", "SkippedMinorVersion"),

        // Major downgrades are forbidden
        Arguments.of("9.0.0", "8.7.0", "MajorDowngrade"));
  }

  @ParameterizedTest
  @MethodSource("forbiddenUpgradeScenarios")
  void shouldThrowExceptionForForbiddenUpgrades(
      final String previousVersion,
      final String currentVersion,
      final String expectedIncompatibleType) {

    // Given
    config.schemaManager().getRetry().setMaxRetries(2);
    schemaManager = createSpySchemaManager(currentVersion);
    mockSchemaVersionInMetadata(previousVersion);

    // When & Then: Should throw exception for forbidden upgrades
    assertThatThrownBy(() -> schemaManager.startup())
        .isInstanceOf(IncompatibleVersionException.class)
        .hasMessageContaining("Schema is not compatible with current version")
        .hasMessageContaining(expectedIncompatibleType);
    // assert that the version check is not retried multiple times
    verify(searchEngineClient, times(1)).indexExists(metadataIndex.getFullQualifiedName());
    // No operations should be invoked since the upgrade is forbidden
    verifyNoOperationWereInvoked();
  }

  // Test data for forbidden upgrades
  static Stream<Arguments> forbiddenPreReleaseUpgradeScenarios() {
    return Stream.of(
        Arguments.of("8.8.0", "8.9.0-alpha1", "UseOfPreReleaseVersion"),
        Arguments.of("8.8.0-rc1", "8.8.0", "UseOfPreReleaseVersion"),
        Arguments.of("8.8.0-SNAPSHOT", "8.9.0", "UseOfPreReleaseVersion"));
  }

  @ParameterizedTest
  @MethodSource("forbiddenPreReleaseUpgradeScenarios")
  void shouldThrowExceptionForPreReleaseUpgrades(
      final String previousVersion,
      final String currentVersion,
      final String expectedIncompatibleType) {

    // Given
    config.schemaManager().getRetry().setMaxRetries(2);
    schemaManager = createSpySchemaManager(currentVersion);
    mockSchemaVersionInMetadata(previousVersion);

    // When & Then: Should throw exception for forbidden upgrades
    assertThatThrownBy(() -> schemaManager.startup())
        .isInstanceOf(IncompatibleVersionException.class)
        .hasMessageContaining("Cannot upgrade to or from a pre-release version")
        .hasMessageContaining(expectedIncompatibleType);
    // assert that the version check is not retried multiple times
    verify(searchEngineClient, times(1)).indexExists(metadataIndex.getFullQualifiedName());
    // No operations should be invoked since the upgrade is forbidden
    verifyNoOperationWereInvoked();
  }

  // Test data for allowed upgrades
  static Stream<Arguments> allowedUpgradeScenarios() {
    return Stream.of(
        // Same version - no schema upgrade for release versions
        Arguments.of("8.8.0", "8.8.0", false, "SameVersion"),

        // Same version with SNAPSHOT - should trigger schema upgrade
        Arguments.of("8.8.0-SNAPSHOT", "8.8.0-SNAPSHOT", true, "SameVersion"),

        // Patch upgrades - should trigger schema upgrade
        Arguments.of("8.8.0", "8.8.1", true, "PatchUpgrade"),
        Arguments.of("8.8.1", "8.8.3", true, "PatchUpgrade"),

        // Minor upgrades - should trigger schema upgrade
        Arguments.of("8.8.0", "8.9.0", true, "MinorUpgrade"),
        Arguments.of("8.8.2", "8.9.1", true, "MinorUpgrade"),

        // First time setup (no previous version) - should trigger schema upgrade
        Arguments.of(null, "8.8.0", true, "PreviousVersionUnknown"));
  }

  @ParameterizedTest
  @MethodSource("allowedUpgradeScenarios")
  void shouldAllowCompatibleUpgrades(
      final String previousVersion,
      final String currentVersion,
      final boolean expectSchemaUpgrade) {

    // Given
    schemaManager = createSpySchemaManager(currentVersion);
    mockSchemaVersionInMetadata(previousVersion);

    // When
    schemaManager.startup();

    // Then: Should complete without throwing exceptions
    verifyInvokedOperations(currentVersion, expectSchemaUpgrade);
  }

  private void verifyInvokedOperations(
      final String currentVersion, final boolean expectSchemaUpgrade) {
    if (expectSchemaUpgrade) {
      // Verify schema upgrade operations were called via searchEngineClient
      verify(searchEngineClient)
          .upsertDocument(
              metadataIndex.getFullQualifiedName(),
              SCHEMA_VERSION_METADATA_ID,
              Map.of(
                  MetadataIndex.ID,
                  SCHEMA_VERSION_METADATA_ID,
                  MetadataIndex.VALUE,
                  currentVersion));
      Stream.of(metadataIndex, testIndexDescriptor, testTemplateDescriptor)
          .forEach(
              indexDescriptor ->
                  verify(searchEngineClient).createIndex(indexDescriptor, config.index()));
      verify(searchEngineClient).createIndexTemplate(testTemplateDescriptor, config.index(), true);
    } else {
      // Verify schema upgrade was skipped - no upsert should happen for same version
      verify(searchEngineClient, never()).upsertDocument(anyString(), anyString(), any());
      verify(searchEngineClient, never()).createIndex(any(), any());
      verify(searchEngineClient, never()).createIndexTemplate(any(), any(), anyBoolean());
    }

    // Settings and lifecycle policies should always be updated unless returning early
    Stream.of(metadataIndex, testIndexDescriptor, testTemplateDescriptor)
        .forEach(
            indexDescriptor ->
                verify(searchEngineClient)
                    .putSettings(
                        List.of(indexDescriptor), Map.of("index.number_of_replicas", "1")));
    searchEngineClient.putIndexLifeCyclePolicy(
        config.retention().getPolicyName(), config.retention().getMinimumAge());
    searchEngineClient.putIndexLifeCyclePolicy(
        config.retention().getUsageMetricsPolicyName(),
        config.retention().getUsageMetricsMinimumAge());
  }

  // Test data for skipped upgrades (downgrades that should return early)
  static Stream<Arguments> skippedUpgradeScenarios() {
    return Stream.of(
        // Minor downgrades - should skip all operations
        Arguments.of("8.9.0", "8.8.0", "MinorDowngrade"),
        Arguments.of("8.9.2", "8.8.1", "MinorDowngrade"),

        // Patch downgrades - should skip all operations
        Arguments.of("8.8.2", "8.8.0", "PatchDowngrade"),
        Arguments.of("8.8.5", "8.8.1", "PatchDowngrade"));
  }

  @ParameterizedTest
  @MethodSource("skippedUpgradeScenarios")
  void shouldSkipOperationsForDowngrades(
      final String previousVersion, final String currentVersion) {

    // Given
    schemaManager = createSpySchemaManager(currentVersion);
    mockSchemaVersionInMetadata(previousVersion);

    // When
    schemaManager.startup();

    // Then: Should return early without performing any operations
    verifyNoOperationWereInvoked();
  }

  private void verifyNoOperationWereInvoked() {
    verify(searchEngineClient, never()).upsertDocument(anyString(), anyString(), any());
    verify(searchEngineClient, never()).createIndex(any(), any());
    verify(searchEngineClient, never()).putSettings(any(), any());
    verify(searchEngineClient, never()).putIndexLifeCyclePolicy(any(), any());
  }

  @Test
  void shouldIgnoreForbiddenUpgradesWhenVersionCheckDisabled() {
    // Given: Version check restriction is disabled
    config.schemaManager().setVersionCheckRestrictionEnabled(false);
    final var spySchemaManager = createSpySchemaManager("9.0.0");
    mockSchemaVersionInMetadata("8.8.0");

    // When: Should not throw exception when version check is disabled
    spySchemaManager.startup();

    // Then: Should proceed with schema upgrade despite incompatibility
    verifyInvokedOperations("9.0.0", true);
  }

  @Test
  void shouldHandleUnknownPreviousVersionAsFirstTimeSetup() {
    // Given: No previous version stored (fresh installation)
    final var spySchemaManager = createSpySchemaManager("8.8.0");
    mockSchemaVersionInMetadata(null);

    // When
    spySchemaManager.startup();

    // Then: Should proceed with full schema initialization
    verifyInvokedOperations("8.8.0", true);
  }

  @Test
  void shouldTriggerSchemaUpgradeForSnapshotVersions() {
    // Given: Same version but with SNAPSHOT suffix
    final var spySchemaManager = createSpySchemaManager("8.8.0-SNAPSHOT");
    mockSchemaVersionInMetadata("8.8.0-SNAPSHOT");

    // When
    spySchemaManager.startup();

    // Then: Should trigger schema upgrade for SNAPSHOT versions even when same version
    verifyInvokedOperations("8.8.0-SNAPSHOT", true);
  }

  @Test
  void shouldHandleMissingSchemaMetadataIndex() {
    // Given: Schema metadata index does not exist (fresh installation)
    schemaManager = createSpySchemaManager("8.8.0");

    // Mock that the schema metadata index specifically does not exist
    when(searchEngineClient.indexExists(metadataIndex.getFullQualifiedName())).thenReturn(false);

    // No previous version stored (fresh installation)
    mockSchemaVersionInMetadata(null);

    // When
    schemaManager.startup();

    // Then: Should proceed with full schema initialization including metadata index creation
    verifyInvokedOperations("8.8.0", true);
  }

  private SchemaManager createSpySchemaManager(final String currentVersion) {
    return spy(
        new SchemaManager(
            searchEngineClient,
            indexDescriptors,
            templateDescriptors,
            config,
            mock(IndexSchemaValidator.class),
            currentVersion,
            null));
  }

  private void mockSchemaVersionInMetadata(final String version) {
    final var versionDoc =
        version == null
            ? null
            : Map.<String, Object>of(ID, SCHEMA_VERSION_METADATA_ID, VALUE, version);
    when(searchEngineClient.getDocument(
            metadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID))
        .thenReturn(versionDoc);
  }
}
