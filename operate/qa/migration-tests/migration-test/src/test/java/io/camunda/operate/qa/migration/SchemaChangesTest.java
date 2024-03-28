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
package io.camunda.operate.qa.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.SchemaManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.SearchHit;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SchemaChangesTest extends AbstractMigrationTest {

  @Autowired protected SchemaManager schemaManager;

  /** This is a good candidate for a parameterized test when we test new versions */
  @Test
  public void shouldHaveAddedFieldsWith85() {
    // given
    final List<IndexChange> expectedIndexChanges =
        Arrays.asList(
            IndexChange.forVersionAndIndex("8.5", listViewTemplate.getDerivedIndexNamePattern())
                .withAddedProperty("position", "long")
                .withAddedProperty("positionIncident", Map.of("type", "long", "index", false))
                .withAddedProperty("positionJob", Map.of("type", "long", "index", false)),
            IndexChange.forVersionAndIndex("8.5", eventTemplate.getDerivedIndexNamePattern())
                .withAddedProperty("position", "long")
                .withAddedProperty("positionIncident", Map.of("type", "long", "index", false))
                .withAddedProperty(
                    "positionProcessMessageSubscription", Map.of("type", "long", "index", false))
                .withAddedProperty("positionJob", Map.of("type", "long", "index", false)),
            IndexChange.forVersionAndIndex("8.5", incidentTemplate.getDerivedIndexNamePattern())
                .withAddedProperty("position", "long"),
            IndexChange.forVersionAndIndex("8.5", variableTemplate.getDerivedIndexNamePattern())
                .withAddedProperty("position", "long"));

    // then
    expectedIndexChanges.forEach(
        expectedChange -> {
          final Map<String, IndexMapping> mappings =
              schemaManager.getIndexMappings(expectedChange.indexPattern);
          assertThat(mappings).isNotEmpty();

          mappings.forEach(
              (indexName, actualMapping) -> {
                verifyIndexesIsChanged(indexName, actualMapping, expectedChange);
                verifyArbitraryDocumentIsNotChanged(indexName, expectedChange);
              });
        });
  }

  private void verifyIndexesIsChanged(
      final String indexName, final IndexMapping actualMapping, final IndexChange expectedChange) {
    assertThat(expectedChange.isReflectedBy(actualMapping))
        .withFailMessage(
            "Expecting index %s to have changes:\n%s\nActual mapping:\n%s",
            indexName, expectedChange, actualMapping)
        .isTrue();
  }

  /** Validates that documents were not migrated/reindexed */
  protected void verifyArbitraryDocumentIsNotChanged(
      final String indexName, final IndexChange expectedChange) {

    final SearchRequest searchRequest = new SearchRequest(indexName);
    searchRequest.source().size(1).fetchField("position");

    final List<SearchHit> documents = entityReader.searchDocumentsFor(searchRequest);

    final SearchHit document = documents.get(0);

    assertThat(expectedChange.isNotReflectedBy(document))
        .withFailMessage(
            "Expecting document %s in index %s to not have changes:\n%s\nActual document:\n%s",
            document.getId(), indexName, expectedChange, document.getSourceAsMap())
        .isTrue();
  }

  protected static class IndexChange {

    protected String versionName;
    protected String indexPattern;
    protected List<IndexMappingProperty> addedProperties = new ArrayList<>();

    protected static IndexChange forVersionAndIndex(
        final String versionName, final String indexPattern) {
      final IndexChange change = new IndexChange();
      change.versionName = versionName;
      change.indexPattern = indexPattern;
      return change;
    }

    protected IndexChange withAddedProperty(final String name, final String type) {
      final IndexMappingProperty addedProperty = new IndexMappingProperty();
      addedProperty.setName(name);
      addedProperty.setTypeDefinition(Map.of("type", type));
      addedProperties.add(addedProperty);

      return this;
    }

    protected IndexChange withAddedProperty(
        final String name, final Map<String, Object> typeDefinition) {
      final IndexMappingProperty addedProperty = new IndexMappingProperty();
      addedProperty.setName(name);
      addedProperty.setTypeDefinition(typeDefinition);
      addedProperties.add(addedProperty);
      return this;
    }

    public String getIndexPattern() {
      return indexPattern;
    }

    public String getVersionName() {
      return versionName;
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder();
      sb.append("{version=");
      sb.append(versionName);
      sb.append(", indexPattern=");
      sb.append(indexPattern);
      sb.append(", addedProperties=");
      sb.append(addedProperties);
      sb.append("}");

      return sb.toString();
    }

    public boolean isReflectedBy(final IndexMapping actualMapping) {

      return actualMapping.getProperties().containsAll(addedProperties);
    }

    public boolean isNotReflectedBy(final SearchHit document) {

      final Map<String, Object> documentSource = document.getSourceAsMap();
      final Set<String> unmatchedAddedProperties =
          addedProperties.stream().map(IndexMappingProperty::getName).collect(Collectors.toSet());
      unmatchedAddedProperties.retainAll(documentSource.keySet());

      // all new properties should have been removed
      return unmatchedAddedProperties.isEmpty();
    }
  }
}
