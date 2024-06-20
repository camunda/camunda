/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static io.camunda.operate.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OpensearchClientProvider;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import io.camunda.operate.qa.util.TestOpensearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      OperateProperties.class,
      DatabaseInfo.class,
      IndexSchemaValidator.class,
      TestElasticsearchSchemaManager.class,
      TestOpensearchSchemaManager.class,
      RichOpenSearchClient.class,
      OpensearchClientProvider.class,
      IndexDescriptor.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      // Assume we have only 3 indices:
      ProcessIndex.class,
      UserIndex.class,
      IncidentTemplate.class
    })
public class IndexOldSchemaValidatorIT {

  @MockBean RetryElasticsearchClient retryElasticsearchClient;

  @MockBean RichOpenSearchClient richOpenSearchClient;

  @Autowired List<IndexDescriptor> indexDescriptors;

  @Autowired ProcessIndex processIndex;

  @Autowired IndexSchemaValidator indexSchemaValidator;

  @Autowired OperateProperties operateProperties;

  private String operatePrefix;

  private List<String> allIndexNames;
  private List<String> allIndexAliases;

  private Set<String> newerVersions;

  private Set<String> olderVersions;

  @Before
  public void setUp() {
    operatePrefix =
        DatabaseInfo.isOpensearch()
            ? operateProperties.getOpensearch().getIndexPrefix()
            : operateProperties.getElasticsearch().getIndexPrefix();
    allIndexNames =
        indexDescriptors.stream()
            .map(IndexDescriptor::getFullQualifiedName)
            .collect(Collectors.toList());
    allIndexAliases =
        indexDescriptors.stream().map(IndexDescriptor::getAlias).collect(Collectors.toList());
    newerVersions = Set.of("100.1.1", "100.1.2", "100.0.1", "100.2.3");
    olderVersions = Set.of("0.2.5", "0.1.2");
  }

  @Test
  public void testHasAnyOperateIndices() {
    // No indices
    whenDatabaseClientReturnsIndexNames(Set.of());
    assertThat(indexSchemaValidator.hasAnyOperateIndices()).isFalse();
    // At least one operate index
    whenDatabaseClientReturnsIndexNames(Set.of(operatePrefix + "-index", "not-operate"));
    assertThat(indexSchemaValidator.hasAnyOperateIndices()).isTrue();
  }

  @Test
  public void testSchemaExists() {
    // No indices
    whenDatabaseClientReturnsIndexNames(Set.of());
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // Only 2 operate indices
    whenDatabaseClientReturnsIndexNames(Set.of(allIndexNames.get(0), allIndexNames.get(1)));
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // All indices, but no aliases
    whenDatabaseClientReturnsIndexNames(new HashSet<>(allIndexNames));
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // All indices, but only two aliases
    whenDatabaseClientReturnsIndexNames(
        new HashSet<>(allIndexNames), Set.of(allIndexAliases.get(0), allIndexAliases.get(1)));
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    // All operate indices
    whenDatabaseClientReturnsIndexNames(
        new HashSet<>(allIndexNames), new HashSet<>(allIndexAliases));
    assertThat(indexSchemaValidator.schemaExists()).isTrue();
  }

  @Test
  public void testNewerVersionsForIndex() {

    // Only older versions
    whenDatabaseClientReturnsIndexNames(versionsOf(processIndex, olderVersions));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).isEmpty();
    // Only current version
    whenDatabaseClientReturnsIndexNames(
        versionsOf(processIndex, Set.of(processIndex.getVersion())));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).isEmpty();
    // Only newer versions
    whenDatabaseClientReturnsIndexNames(versionsOf(processIndex, newerVersions));
    assertThat(indexSchemaValidator.newerVersionsForIndex(processIndex)).containsAll(newerVersions);
  }

  @Test
  public void testOlderVersionsForIndex() {
    // Only newer versions
    whenDatabaseClientReturnsIndexNames(versionsOf(processIndex, newerVersions));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEmpty();
    // Only current version
    whenDatabaseClientReturnsIndexNames(
        versionsOf(processIndex, Set.of(processIndex.getVersion())));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEmpty();
    // Only older versions
    whenDatabaseClientReturnsIndexNames(versionsOf(processIndex, olderVersions));
    assertThat(indexSchemaValidator.olderVersionsForIndex(processIndex)).isEqualTo(olderVersions);
  }

  @Test
  public void testIsValid() {
    // No indices
    whenDatabaseClientReturnsIndexNames(Set.of());
    indexSchemaValidator.validateIndexVersions();

    // Current indices
    whenDatabaseClientReturnsIndexNames(new HashSet<>(allIndexNames));
    indexSchemaValidator.validateIndexVersions();

    // 1 older version for index
    whenDatabaseClientReturnsIndexNames(Set.of(getFullQualifiedIndexName(processIndex, "0.9.0")));
    indexSchemaValidator.validateIndexVersions();

    // two indices with one name substring of another name
    whenDatabaseClientReturnsIndexNames(
        Set.of(
            getFullQualifiedIndexName("process", "0.8.0"),
            getFullQualifiedIndexName("process-instance", "0.9.0")));
    indexSchemaValidator.validateIndexVersions();

    // two indices with one name substring of another name
    whenDatabaseClientReturnsIndexNames(
        Set.of(
            getFullQualifiedIndexName("operation", "0.8.0"),
            getFullQualifiedIndexName("batch-operation", "0.9.0")));
    indexSchemaValidator.validateIndexVersions();
  }

  @Test
  public void testIsNotValidForMoreThanOneOlderVersion() {
    // 2 older version for index
    whenDatabaseClientReturnsIndexNames(
        Set.of(
            getFullQualifiedIndexName(processIndex, "0.9.0"),
            getFullQualifiedIndexName(processIndex, "0.8.0")));
    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> indexSchemaValidator.validateIndexVersions())
        .withMessageContaining(
            "More than one older version for process ("
                + processIndex.getVersion()
                + ") found: [0.8.0, 0.9.0]");
  }

  @Test
  public void testIsNotValidForANewerVersion() {
    // 1 newer version for index
    final var newerVersion = "10.0.0";
    whenDatabaseClientReturnsIndexNames(
        Set.of(getFullQualifiedIndexName(processIndex, newerVersion)));
    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> indexSchemaValidator.validateIndexVersions())
        .withMessageContaining(
            "Newer version(s) for process ("
                + processIndex.getVersion()
                + ") already exists: ["
                + newerVersion
                + "]");
  }

  private void whenDatabaseClientReturnsIndexNames(final Set<String> givenIndexNames) {
    whenDatabaseClientReturnsIndexNames(givenIndexNames, null);
  }

  private void mockElasticsearchReturnIndexNames(
      final Set<String> givenIndexNames, final Set<String> givenAliasesNames) {
    when(retryElasticsearchClient.getIndexNames(anyString())).thenReturn(givenIndexNames);
    if (givenAliasesNames != null) {
      when(retryElasticsearchClient.getAliasesNames(anyString())).thenReturn(givenAliasesNames);
    }
  }

  private void mockOpenSearchReturnIndexNames(
      final Set<String> givenIndexNames, final Set<String> givenAliasesNames) {
    final OpenSearchIndexOperations indexMock = mock(OpenSearchIndexOperations.class);
    when(indexMock.getIndexNamesWithRetries(anyString())).thenReturn(givenIndexNames);
    if (givenAliasesNames != null) {
      when(indexMock.getAliasesNamesWithRetries(anyString())).thenReturn(givenAliasesNames);
    }
    when(richOpenSearchClient.index()).thenReturn(indexMock);
  }

  private void whenDatabaseClientReturnsIndexNames(
      final Set<String> givenIndexNames, final Set<String> givenAliasesNames) {
    mockElasticsearchReturnIndexNames(givenIndexNames, givenAliasesNames);
    mockOpenSearchReturnIndexNames(givenIndexNames, givenAliasesNames);
  }

  private Set<String> versionsOf(final IndexDescriptor index, final Set<String> versions) {
    return new HashSet<>(map(versions, version -> getFullQualifiedIndexName(index, version)));
  }

  // See AbstractIndexDescriptor::getFullQualifiedIndexName
  private String getFullQualifiedIndexName(final IndexDescriptor index, final String version) {
    return getFullQualifiedIndexName(index.getIndexName(), version);
  }

  private String getFullQualifiedIndexName(final String indexNamePart, final String version) {
    return String.format("%s-%s-%s_", operatePrefix, indexNamePart, version);
  }
}
