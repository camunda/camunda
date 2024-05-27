/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.qa.migration.util.AbstractMigrationTest;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.manager.SchemaManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@RunWith(SpringRunner.class)
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.tasklist.property",
      "io.camunda.tasklist.schema.indices",
      "io.camunda.tasklist.schema.templates",
      "io.camunda.tasklist.qa.migration",
      "io.camunda.tasklist.util"
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class SchemaChangesTest extends AbstractMigrationTest {

  @Autowired protected SchemaManager schemaManager;

  @Test
  public void shouldHaveAddedFields() {
    // given

    // from now on, each time a new field is added to the index, a new IndexChange object should be
    // created
    // this can be tested adding the index change to the expectedIndexChanges list below
    final List<IndexChange> expectedIndexChanges =
        Arrays.asList(
            IndexChange.forVersionAndIndex("8.6.0", processIndex.getDerivedIndexNamePattern())
                .withAddedProperty("bpmnXml", "text"));

    // then
    expectedIndexChanges.forEach(
        expectedChange -> {
          final Map<String, IndexMapping> mappings;
          try {
            mappings = schemaManager.getIndexMappings(expectedChange.indexPattern);
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
          assertThat(mappings).isNotEmpty();

          mappings.forEach(
              (indexName, actualMapping) -> {
                verifyIndexesIsChanged(indexName, actualMapping, expectedChange);
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
  }
}
