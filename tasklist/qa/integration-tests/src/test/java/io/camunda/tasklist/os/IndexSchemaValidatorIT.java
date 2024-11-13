/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import static io.camunda.tasklist.schema.v86.indices.AbstractIndexDescriptor.formatPrefixAndComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.v86.IndexSchemaValidator;
import io.camunda.tasklist.schema.v86.manager.SchemaManager;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IndexSchemaValidatorIT extends TasklistIntegrationTest {

  private static final String ORIGINAL_SCHEMA_PATH_OPENSEARCH =
      "/tasklist-test-opensearch-schema-validator.json";
  private static final String INDEX_NAME = "test";

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private RetryOpenSearchClient retryOpenSearchClient;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @Autowired private SchemaManager schemaManager;

  private String originalSchemaContent;
  private IndexDescriptor indexDescriptor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @BeforeEach
  public void setUp() throws Exception {
    indexDescriptor = createIndexDescriptor();
    originalSchemaContent = readSchemaContent();
    assertThat(originalSchemaContent).doesNotContain("\"prop2\"");
  }

  @AfterEach
  public void tearDown() throws Exception {
    restoreOriginalSchemaContent();
    retryOpenSearchClient.deleteIndicesFor(getFullIndexName());
  }

  @Test
  public void shouldValidateDynamicIndexWithAddedProperty() throws Exception {
    replaceIndexDescriptorsInValidator(Collections.singleton(indexDescriptor));
    schemaManager.createIndex(indexDescriptor);

    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();

    createDocument("prop0", "test");

    updateSchemaContent(
        originalSchemaContent.replace(
            "\"properties\": {", "\"properties\": {\n    \"prop2\": { \"type\": \"keyword\" },"));

    final String newSchemaContent = readSchemaContent();
    assertThat(newSchemaContent).contains("\"prop2\"");

    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isNotEmpty();
  }

  @Test
  public void shouldValidateDynamicIndexWithRemovedPropertyAndWillIgnoreRemovals()
      throws Exception {
    replaceIndexDescriptorsInValidator(Collections.singleton(indexDescriptor));
    schemaManager.createIndex(indexDescriptor);

    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();

    createDocument("prop0", "test");

    updateSchemaContent(
        originalSchemaContent.replace(
            "\"properties\": {\n"
                + "    \"prop0\": {\n"
                + "      \"type\": \"keyword\"\n"
                + "    },",
            "\"properties\": {"));

    final String newSchemaContent = readSchemaContent();
    assertThat(newSchemaContent).doesNotContain("\"prop0\"");

    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();
  }

  private IndexDescriptor createIndexDescriptor() {
    return new IndexDescriptor() {
      @Override
      public String getFullQualifiedName() {
        return getFullIndexName();
      }

      @Override
      public String getAlias() {
        return getFullQualifiedName() + "alias";
      }

      @Override
      public String getIndexName() {
        return INDEX_NAME;
      }

      @Override
      public String getMappingsClasspathFilename() {
        return ORIGINAL_SCHEMA_PATH_OPENSEARCH;
      }

      @Override
      public String getAllVersionsIndexNameRegexPattern() {
        return getFullIndexName() + "*";
      }

      @Override
      public String getVersion() {
        return "1.0.0";
      }
    };
  }

  private String readSchemaContent() throws Exception {
    return new String(
        Files.readAllBytes(
            Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH_OPENSEARCH).toURI())));
  }

  private void restoreOriginalSchemaContent() throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH_OPENSEARCH).toURI()),
        originalSchemaContent.getBytes(),
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void createDocument(final String key, final String value) throws Exception {
    final Map<String, Object> document = Map.of(key, value);
    final boolean created =
        retryOpenSearchClient.createOrUpdateDocument(
            indexDescriptor.getFullQualifiedName(), "id", document);
    System.out.println("Created: " + created);
  }

  private void updateSchemaContent(final String content) throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH_OPENSEARCH).toURI()),
        content.getBytes(),
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void replaceIndexDescriptorsInValidator(final Set<IndexDescriptor> newIndexDescriptors)
      throws NoSuchFieldException, IllegalAccessException {
    final Field field = indexSchemaValidator.getClass().getDeclaredField("indexDescriptors");
    field.setAccessible(true);
    field.set(indexSchemaValidator, newIndexDescriptors);
  }

  private String getFullIndexName() {
    return formatPrefixAndComponent(schemaManager.getIndexPrefix()) + "-" + INDEX_NAME;
  }
}
