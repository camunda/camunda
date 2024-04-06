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
package io.camunda.tasklist.es;

import static io.camunda.tasklist.util.CollectionUtil.filter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.indices.MigrationRepositoryIndex;
import io.camunda.tasklist.schema.indices.TasklistWebSessionIndex;
import io.camunda.tasklist.schema.migration.ProcessorStep;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

public class SchemaCreationIT extends TasklistIntegrationTest {

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private NoSqlHelper noSqlHelper;

  @Test
  public void testIndexCreation() throws IOException {
    for (IndexDescriptor indexDescriptor : indexDescriptors) {
      assertIndexAndAlias(indexDescriptor.getFullQualifiedName(), indexDescriptor.getAlias());
    }

    // assert schema creation won't be performed for the second time
    assertThat(indexSchemaValidator.schemaExists()).isTrue();
  }

  @Test // ZTL-1007
  public void testMigrationStepsRepositoryFields() throws IOException {
    final IndexDescriptor migrationStepsIndexDescriptor =
        getIndexDescriptorBy(MigrationRepositoryIndex.INDEX_NAME);
    assertThat(migrationStepsIndexDescriptor.getVersion()).isEqualTo("1.1.0");
    assertThat(getFieldDescriptions(migrationStepsIndexDescriptor).keySet())
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

  @Test // ZTL-1010
  public void testDynamicMappingsOfIndices() throws Exception {
    final IndexDescriptor sessionIndex =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    indexDescriptor.getIndexName().equals(TasklistWebSessionIndex.INDEX_NAME))
            .findFirst()
            .orElseThrow();
    assertThatIndexHasDynamicMappingOf(sessionIndex, "true");

    final List<IndexDescriptor> strictMappingIndices =
        indexDescriptors.stream()
            .filter(
                indexDescriptor ->
                    !indexDescriptor.getIndexName().equals(TasklistWebSessionIndex.INDEX_NAME))
            .collect(Collectors.toList());

    for (IndexDescriptor indexDescriptor : strictMappingIndices) {
      assertThatIndexHasDynamicMappingOf(indexDescriptor, "strict");
    }
  }

  private Map<String, Object> getFieldDescriptions(IndexDescriptor indexDescriptor)
      throws IOException {
    return noSqlHelper.getFieldDescription(indexDescriptor);
  }

  private IndexDescriptor getIndexDescriptorBy(String name) {
    return filter(indexDescriptors, indexDescriptor -> indexDescriptor.getIndexName().equals(name))
        .get(0);
  }

  private void assertIndexAndAlias(String indexName, String aliasName) throws IOException {
    assertThat(noSqlHelper.indexHasAlias(indexName, aliasName)).isTrue();
  }

  private void assertThatIndexHasDynamicMappingOf(
      final IndexDescriptor indexDescriptor, String dynamicMapping) throws IOException {
    assertThat(noSqlHelper.isIndexDynamicMapping(indexDescriptor, dynamicMapping)).isTrue();
  }
}
