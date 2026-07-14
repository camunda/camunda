/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.SchemaMetadataStore.CLUSTER_ID_METADATA_ID;
import static io.camunda.search.schema.SchemaMetadataStore.SCHEMA_VERSION_METADATA_ID;
import static io.camunda.webapps.schema.descriptors.index.MetadataIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.MetadataIndex.VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.IncompatibleClusterIdException;
import io.camunda.search.schema.exceptions.IncompatibleVersionException;
import io.camunda.search.schema.utils.TestIndexDescriptor;
import io.camunda.search.schema.utils.TestTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

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
    } else {
      // Verify schema upgrade was skipped - no upsert should happen for same version
      verify(searchEngineClient, never()).upsertDocument(anyString(), anyString(), any());
      verify(searchEngineClient, never()).createIndex(any(), any());
    }

    // Creating missing index templates is always invoked
    verify(searchEngineClient).createIndexTemplate(testTemplateDescriptor, config.index(), true);
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

  @Test
  void shouldThrowExceptionForClusterIdMismatch() {
    // Given: validateClusterId() is called directly by the exporter (e.g. CamundaExporter.open()),
    // independently of startup() - it runs once the exporter has resolved its own cluster ID,
    // which startup() (broker-wide, pre-gossip) never has.
    schemaManager = createSpySchemaManager("8.8.0", "this-cluster");
    mockClusterIdInMetadata("other-cluster");

    // When & Then
    assertThatThrownBy(() -> schemaManager.validateClusterId())
        .isInstanceOf(IncompatibleClusterIdException.class)
        .hasMessageContaining("other-cluster")
        .hasMessageContaining("this-cluster");
    verify(searchEngineClient, never()).upsertDocument(anyString(), anyString(), any());
  }

  @Test
  void shouldIgnoreClusterIdMismatchWhenRestrictionDisabled() {
    // Given
    config.schemaManager().setClusterIdCheckRestrictionEnabled(false);
    schemaManager = createSpySchemaManager("8.9.0", "this-cluster");
    mockClusterIdInMetadata("other-cluster");

    // When: should not throw despite the mismatch
    schemaManager.validateClusterId();

    // Then: the mismatched value is still (re-)recorded, matching the ES/OS bootstrap semantics
    verify(searchEngineClient)
        .upsertDocument(
            metadataIndex.getFullQualifiedName(),
            CLUSTER_ID_METADATA_ID,
            Map.of(MetadataIndex.ID, CLUSTER_ID_METADATA_ID, MetadataIndex.VALUE, "this-cluster"));
  }

  @Test
  void shouldProceedWhenClusterIdMatches() {
    // Given
    schemaManager = createSpySchemaManager("8.9.0", "this-cluster");
    mockClusterIdInMetadata("this-cluster");

    // When
    schemaManager.validateClusterId();

    // Then - no exception, and no redundant write since nothing changed
    verify(searchEngineClient, never())
        .upsertDocument(
            eq(metadataIndex.getFullQualifiedName()), eq(CLUSTER_ID_METADATA_ID), any());
  }

  @Test
  void shouldStoreClusterIdOnFreshInstall() {
    // Given: no previous cluster ID stored (fresh installation, or upgrading from before this
    // feature existed)
    schemaManager = createSpySchemaManager("8.9.0", "this-cluster");
    mockClusterIdInMetadata(null);

    // When
    schemaManager.validateClusterId();

    // Then
    verify(searchEngineClient)
        .upsertDocument(
            metadataIndex.getFullQualifiedName(),
            CLUSTER_ID_METADATA_ID,
            Map.of(MetadataIndex.ID, CLUSTER_ID_METADATA_ID, MetadataIndex.VALUE, "this-cluster"));
  }

  @Test
  void shouldSkipClusterIdCheckAndStorageWhenLocalClusterIdUnresolved() {
    // Given: this exporter could not resolve its own cluster ID (e.g. empty string sentinel)
    schemaManager = createSpySchemaManager("8.9.0", "");
    mockClusterIdInMetadata("other-cluster");

    // When: should not throw, and should not overwrite the stored cluster ID
    schemaManager.validateClusterId();

    // Then
    verify(searchEngineClient, never())
        .upsertDocument(
            eq(metadataIndex.getFullQualifiedName()), eq(CLUSTER_ID_METADATA_ID), any());
  }

  private SchemaManager createSpySchemaManager(final String currentVersion) {
    return createSpySchemaManager(currentVersion, null);
  }

  private SchemaManager createSpySchemaManager(
      final String currentVersion, final String clusterId) {
    return spy(
        new SchemaManager(
            searchEngineClient,
            indexDescriptors,
            templateDescriptors,
            config,
            mock(IndexSchemaValidator.class),
            currentVersion,
            null,
            clusterId));
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

  private void mockClusterIdInMetadata(final String clusterId) {
    final var clusterIdDoc =
        clusterId == null
            ? null
            : Map.<String, Object>of(ID, CLUSTER_ID_METADATA_ID, VALUE, clusterId);
    when(searchEngineClient.getDocument(
            metadataIndex.getFullQualifiedName(), CLUSTER_ID_METADATA_ID))
        .thenReturn(clusterIdDoc);
  }

  @Nested
  class ShardCountPrecedenceTest {

    private SearchEngineClient client;
    private SearchEngineConfiguration cfg;
    private MetadataIndex metaIndex;

    @BeforeEach
    void setUp() {
      client = mock(SearchEngineClient.class);
      cfg = SearchEngineConfiguration.of(c -> c);
      cfg.schemaManager().setCreateSchema(true);
      cfg.schemaManager().getRetry().setMaxRetries(1);
      cfg.connect().setIndexPrefix("test");
      metaIndex = new MetadataIndex("test", true);
      when(client.indexExists(metaIndex.getFullQualifiedName())).thenReturn(true);
    }

    private SchemaManager buildManager(
        final List<IndexDescriptor> indices, final List<IndexTemplateDescriptor> templates) {
      final var mgr =
          new SchemaManager(
              client, indices, templates, cfg, mock(IndexSchemaValidator.class), "8.9.0", null);
      when(client.getDocument(metaIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID))
          .thenReturn(null);
      return mgr;
    }

    @Test
    void shouldUseOneShardForAbstractIndexDescriptorByDefault() {
      // given
      cfg.index().setNumberOfShards(3);
      final var index = new TestIndexDescriptor("test", "mappings.json");
      final var mgr = buildManager(List.of(metaIndex, index), List.of());

      // when
      mgr.startup();

      // then
      final var captor = ArgumentCaptor.forClass(IndexConfiguration.class);
      verify(client).createIndex(eq(index), captor.capture());
      assertThat(captor.getValue().getNumberOfShards()).isEqualTo(1);
    }

    @Test
    void shouldUseGlobalShardCountForAbstractTemplateDescriptorByDefault() {
      // given
      cfg.index().setNumberOfShards(3);
      final var template = new TestTemplateDescriptor("test", "mappings.json");
      final var mgr = buildManager(List.of(metaIndex), List.of(template));

      // when
      mgr.startup();

      // then
      final var captor = ArgumentCaptor.forClass(IndexConfiguration.class);
      verify(client).createIndexTemplate(eq(template), captor.capture(), eq(true));
      assertThat(captor.getValue().getNumberOfShards()).isEqualTo(3);
    }

    @Test
    void shouldPinPostImporterQueueTemplateToOneShard() {
      // given
      cfg.index().setNumberOfShards(5);
      final var template = new PostImporterQueueTemplate("test", true);
      final var mgr = buildManager(List.of(metaIndex), List.of(template));

      // when
      mgr.startup();

      // then
      final var captor = ArgumentCaptor.forClass(IndexConfiguration.class);
      verify(client).createIndexTemplate(eq(template), captor.capture(), eq(true));
      assertThat(captor.getValue().getNumberOfShards()).isEqualTo(1);
    }

    @Test
    void shouldRespectExplicitShardsByIndexNameOverDescriptorDefault() {
      // given — descriptor default would be 1, but explicit config says 7
      cfg.index().setNumberOfShards(3);
      cfg.index().getShardsByIndexName().put("test", 7);
      final var index = new TestIndexDescriptor("test", "mappings.json");
      final var mgr = buildManager(List.of(metaIndex, index), List.of());

      // when
      mgr.startup();

      // then
      final var captor = ArgumentCaptor.forClass(IndexConfiguration.class);
      verify(client).createIndex(eq(index), captor.capture());
      assertThat(captor.getValue().getNumberOfShards()).isEqualTo(7);
    }
  }

  @Nested
  class DeleteArchivedIndicesTest {

    @Test
    void shouldDeleteArchivedIndicesNotMatchingLiveIndices() {
      // given
      schemaManager =
          new SchemaManager(
              searchEngineClient,
              indexDescriptors,
              templateDescriptors,
              config,
              mock(IndexSchemaValidator.class),
              "8.8.0",
              null);

      final var liveIndexName = testTemplateDescriptor.getFullQualifiedName();
      final var archivedIndexName = liveIndexName + "_2024-01-01";
      final var dummyMapping =
          new IndexMapping.Builder().indexName(liveIndexName).dynamic("strict").build();
      final Map<String, IndexMapping> mappings = new HashMap<>();
      mappings.put(liveIndexName, dummyMapping);
      mappings.put(archivedIndexName, dummyMapping);
      when(searchEngineClient.getMappings(anyString(), any())).thenReturn(mappings);

      // when
      schemaManager.deleteArchivedIndices();

      // then
      verify(searchEngineClient, never()).deleteIndex(liveIndexName);
      verify(searchEngineClient).deleteIndex(archivedIndexName);
    }
  }
}
