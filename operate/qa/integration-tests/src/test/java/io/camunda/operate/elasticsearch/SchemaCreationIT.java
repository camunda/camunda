/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      };

  @Autowired private EventTemplate eventTemplate;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private ProcessIndex processIndex;
  @Autowired private DecisionIndex decisionIndex;
  @Autowired private List<IndexDescriptor> indexDescriptors;
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
