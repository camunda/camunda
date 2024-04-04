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
package io.camunda.operate.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.migration.Migrator;
import io.camunda.operate.schema.migration.elasticsearch.ElasticsearchFillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.elasticsearch.ElasticsearchStepsRepository;
import io.camunda.operate.schema.opensearch.OpensearchFillPostImporterQueuePlan;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.opensearch.OpensearchStepsRepository;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.TestSchemaStartup;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.elasticsearch.TestElasticsearchConnector;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.TestOpenSearchConnector;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      IndexSchemaValidator.class,
      Migrator.class,
      ElasticsearchStepsRepository.class,
      ElasticsearchFillPostImporterQueuePlan.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchSchemaTestHelper.class,
      ElasticsearchTaskStore.class,
      TestElasticsearchConnector.class,
      OpensearchStepsRepository.class,
      OpensearchFillPostImporterQueuePlan.class,
      OpensearchSchemaManager.class,
      OpenSearchSchemaTestHelper.class,
      OpensearchTaskStore.class,
      TestOpenSearchConnector.class,
      MigrationRepositoryIndex.class,
      ListViewTemplate.class,
      IncidentTemplate.class,
      PostImporterQueueTemplate.class,
      TestTemplate.class,
      TestIndex.class,
      MigrationProperties.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      TestSchemaStartup.class
    })
public class SchemaStartupIT extends AbstractSchemaIT {

  @Autowired public SchemaManager schemaManager;
  @Autowired public SchemaTestHelper schemaHelper;

  @Autowired public TestSchemaStartup schemaStartup;
  @Autowired public TestIndex testIndex;

  @Test
  public void shouldAddMissingFieldToExistingIndex() throws MigrationException {

    // given
    // a schema with an index missing a field
    schemaManager.createSchema();

    schemaManager.deleteIndicesFor(testIndex.getFullQualifiedName());
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    schemaStartup.initializeSchemaOnDemand();

    // then
    // the index should have been updated
    final String indexName = testIndex.getFullQualifiedName();
    final Map<String, IndexMapping> indexMappings = schemaManager.getIndexMappings(indexName);

    assertThat(indexMappings)
        .containsExactly(
            entry(
                indexName,
                new IndexMapping()
                    .setIndexName(indexName)
                    .setDynamic("strict")
                    .setProperties(
                        Set.of(
                            new IndexMappingProperty()
                                .setName("propB")
                                .setTypeDefinition(Map.of("type", "text")),
                            new IndexMappingProperty()
                                .setName("propA")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propC")
                                .setTypeDefinition(Map.of("type", "keyword"))))));
  }

  @Test
  public void shouldFailIfSchemaNotUpdateable() {

    // given
    // a schema with an index missing a field
    schemaManager.createSchema();

    schemaManager.deleteIndicesFor(testIndex.getFullQualifiedName());
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-nullvalue.json");

    // when / then
    assertThatThrownBy(() -> schemaStartup.initializeSchemaOnDemand())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("Not supported index changes are introduced");
  }
}
