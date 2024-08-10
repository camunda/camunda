/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
