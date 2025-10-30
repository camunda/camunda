/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.camunda.operate.management.IndicesCheck;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.indices.OperateWebSessionIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
public class SchemaCreationIT extends OperateSearchAbstractIT {

  private static final Consumer<OperateProperties> OPERATE_PROPERTIES_CUSTOMIZER =
      operateProperties -> {
        final var numberOfShardsForIndices =
            Map.of(
                ListViewTemplate.INDEX_NAME, 3,
                ProcessIndex.INDEX_NAME, 3);
        final var numberOfReplicasForIndices =
            Map.of(
                ListViewTemplate.INDEX_NAME, 2,
                ProcessIndex.INDEX_NAME, 2);
        operateProperties.getOpensearch().setNumberOfShardsForIndices(numberOfShardsForIndices);
        operateProperties.getOpensearch().setNumberOfReplicasForIndices(numberOfReplicasForIndices);
        operateProperties.getElasticsearch().setNumberOfShardsForIndices(numberOfShardsForIndices);
        operateProperties
            .getElasticsearch()
            .setNumberOfReplicasForIndices(numberOfReplicasForIndices);
        operateProperties.getOpensearch().setIndexTemplatePriority(100);
        operateProperties.getElasticsearch().setIndexTemplatePriority(100);
      };

  @Autowired private EventTemplate eventTemplate;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ProcessIndex processIndex;
  @Autowired private DecisionIndex decisionIndex;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;
  @Autowired private IndicesCheck indicesCheck;
  @Autowired private OperateProperties operateProperties;

  @BeforeAll
  @Override
  public void beforeAllSetup() throws Exception {
    OPERATE_PROPERTIES_CUSTOMIZER.accept(operateProperties);
    super.beforeAllSetup();
  }

  @Test
  public void testIndexCreation() throws IOException {
    for (final IndexDescriptor indexDescriptor : indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    // assert schema creation won't be performed for the second time
    assertThat(indicesCheck.indicesArePresent()).isTrue();
  }

  @Test
  public void testIndexCreationWithCustomNumberOfShards() throws IOException {
    final var settings = testSearchRepository.getIndexSettings(processIndex.getFullQualifiedName());
    assertEquals(Integer.valueOf(3), settings.shards());
    assertEquals(Integer.valueOf(2), settings.replicas());
  }

  @Test
  public void testTemplateIndexCreationWithCustomNumberOfShards() throws IOException {
    final var settings =
        testSearchRepository.getIndexSettings(listViewTemplate.getFullQualifiedName());
    assertEquals(Integer.valueOf(3), settings.shards());
    assertEquals(Integer.valueOf(2), settings.replicas());
  }

  @Test
  public void testIndexCreationWithDefaultNumberOfShards() throws IOException {
    final var settings =
        testSearchRepository.getIndexSettings(decisionIndex.getFullQualifiedName());
    assertEquals(Integer.valueOf(1), settings.shards());
    assertEquals(Integer.valueOf(0), settings.replicas());
  }

  @Test
  public void testTemplateIndexCreationWithDefaultNumberOfShards() throws IOException {
    final var settings =
        testSearchRepository.getIndexSettings(eventTemplate.getFullQualifiedName());
    assertEquals(Integer.valueOf(1), settings.shards());
    assertEquals(Integer.valueOf(0), settings.replicas());
  }

  @Test // OPE-1310
  public void testMigrationStepsRepositoryFields() throws IOException {
    final IndexDescriptor migrationStepsIndexDescriptor =
        getIndexDescriptorBy(MigrationRepositoryIndex.INDEX_NAME);
    assertThat(migrationStepsIndexDescriptor.getVersion()).isEqualTo("1.1.0");
    assertThat(
            testSearchRepository.getFieldNames(
                migrationStepsIndexDescriptor.getFullQualifiedName()))
        .containsExactlyInAnyOrder(
            ProcessorStep.VERSION,
            "@type",
            "description",
            ProcessorStep.APPLIED,
            ProcessorStep.APPLIED_DATE,
            ProcessorStep.CREATED_DATE,
            ProcessorStep.CONTENT,
            ProcessorStep.INDEX_NAME,
            ProcessorStep.ORDER);
  }

  @Test // OPE-1308
  public void testDynamicMappingsOfIndices() throws Exception {
    final IndexDescriptor sessionIndex =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    indexDescriptor.getIndexName().equals(OperateWebSessionIndex.INDEX_NAME))
            .findFirst()
            .orElseThrow();
    assertThatIndexHasDynamicMappingOf(sessionIndex, TestSearchRepository.DynamicMappingType.True);

    final List<IndexDescriptor> strictMappingIndices =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    !indexDescriptor.getIndexName().equals(OperateWebSessionIndex.INDEX_NAME))
            .collect(Collectors.toList());

    for (final IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(
          indexDescriptor, TestSearchRepository.DynamicMappingType.Strict);
    }
  }

  @Test
  void testIndexTemplatePriority() {
    for (final var templateDescriptor : templateDescriptors) {
      assertThat(
              testSearchRepository.getIndexTemplatePriority(templateDescriptor.getTemplateName()))
          .isEqualTo(100L);
    }
  }

  private IndexDescriptor getIndexDescriptorBy(final String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertThatIndexHasDynamicMappingOf(
      final IndexDescriptor indexDescriptor,
      final TestSearchRepository.DynamicMappingType dynamicMappingType)
      throws IOException {
    assertTrue(
        testSearchRepository.hasDynamicMapping(
            indexDescriptor.getFullQualifiedName(), dynamicMappingType));
  }

  private void assertIndexAndAlias(final String indexName, final String aliasName)
      throws IOException {
    final List<String> aliaseNames = testSearchRepository.getAliasNames(indexName);

    assertThat(aliaseNames).hasSize(1);
    assertThat(aliaseNames.get(0)).isEqualTo(aliasName);
  }
}
