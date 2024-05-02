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
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.TestDynamicIndex;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      IndexSchemaValidator.class,
      TestIndex.class,
      TestTemplate.class,
      TestDynamicIndex.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchConnector.class,
      ElasticsearchTaskStore.class,
      ElasticsearchSchemaTestHelper.class,
      OpensearchSchemaManager.class,
      OpensearchConnector.class,
      OpensearchTaskStore.class,
      OpenSearchSchemaTestHelper.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class
    })
@SpringBootTest(properties = {"spring.profiles.active="})
public class IndexSchemaValidatorIT extends AbstractSchemaIT {

  @Autowired public IndexSchemaValidator validator;

  @Autowired public SchemaManager schemaManager;
  @Autowired public SchemaTestHelper schemaHelper;

  @Autowired public TestIndex testIndex;
  @Autowired public TestDynamicIndex testDynamicIndex;
  @Autowired public TestTemplate testTemplate;

  @BeforeEach
  public void createDefault() {
    schemaManager.createDefaults();
  }

  @AfterEach
  public void dropSchema() {
    schemaHelper.dropSchema();
  }

  @Test
  public void shouldValidateDynamicIndexWithAddedProperty() {
    // Create a dynamic index and insert data
    schemaManager.createIndex(
        testDynamicIndex,
        "/schema/elasticsearch/create/index/operate-testdynamicindex-property-removed.json");
    final Map<String, Object> subDocument =
        Map.of(
            "requestedUrl",
            "test",
            "SPRING_SECURITY_CONTEXT",
            "test",
            "SPRING_SECURITY_SAVED_REQUEST",
            "test");
    final Map<String, Object> document = Map.of("propA", "test", "propC", subDocument);
    clientTestHelper.createDocument(testDynamicIndex.getFullQualifiedName(), "1", document);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // Only property B shows up in the diff, and no exception is thrown due to the data adding
    // fields dynamically in propC
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testDynamicIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propB")
                        .setTypeDefinition(Map.of("type", "text")))));
  }

  @Test
  public void shouldValidateDynamicIndexWithDataAddingFields() {
    // Create a dynamic index and insert data
    schemaManager.createIndex(
        testDynamicIndex, "/schema/elasticsearch/create/index/operate-testdynamicindex.json");
    final Map<String, Object> subDocument =
        Map.of(
            "requestedUrl",
            "test",
            "SPRING_SECURITY_CONTEXT",
            "test",
            "SPRING_SECURITY_SAVED_REQUEST",
            "test");
    final Map<String, Object> document =
        Map.of("propA", "test", "propB", "test", "propC", subDocument);
    clientTestHelper.createDocument(testDynamicIndex.getFullQualifiedName(), "1", document);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // No diff should show and no exception thrown due to fields dynamically added from propC data
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldIgnoreMissingIndexes() {
    // given an empty schema

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    // no exception was thrown and:
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldValidateAnUpToDateSchema() {
    // given
    schemaManager.createSchema();

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldDetectAnAddedIndexProperty() {
    // given
    // a schema that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesWithMissingField() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesOnlyOneMissingField() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testIndex,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldDetectAmbiguousIndexDifference() {
    // given
    // a schema with two indices that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    schemaHelper.createIndex(
        testIndex,
        testIndex.getFullQualifiedName() + "2024-01-01",
        "/schema/elasticsearch/create/index/operate-testindex-another-property-removed.json");

    // when/then
    assertThatThrownBy(() -> validator.validateIndexMappings())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining(
            "Ambiguous schema update. First bring runtime and date indices to one schema.");
  }

  @Test
  public void shouldIgnoreARemovedIndexProperty() {
    // given
    // a schema that has a added field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-added.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }

  @Test
  public void shouldDetectChangedIndexMappingParameters() {
    // given
    // a schema that has a added field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-nullvalue.json");

    // when/then
    assertThatThrownBy(() -> validator.validateIndexMappings())
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining(
            "Not supported index changes are introduced. Data migration is required.");
  }

  @Test
  public void shouldDetectAnAddedTemplateProperty() {
    // given
    // a schema that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff)
        .containsExactly(
            entry(
                testTemplate,
                Set.of(
                    new IndexMappingProperty()
                        .setName("propC")
                        .setTypeDefinition(Map.of("type", "keyword")))));
  }

  @Test
  public void shouldIgnoreARemovedTemplateProperty() {
    // given
    // a schema that has an added field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-added.json");

    // when
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // then
    assertThat(indexDiff).isEmpty();
  }
}
