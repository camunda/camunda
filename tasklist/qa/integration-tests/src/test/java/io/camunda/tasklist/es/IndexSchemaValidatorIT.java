/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ApplicationContext associated with this test gets dirty {@link
 * #replaceIndexDescriptorsInValidator(Set)} and should therefore be closed and removed from the
 * context cache.
 */
@DirtiesContext
public class IndexSchemaValidatorIT extends TasklistIntegrationTest {

  private static final String ORIGINAL_SCHEMA_PATH =
      "/tasklist-test-elasticsearch-schema-validator.json";
  private static final String INDEX_NAME = "test";

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Autowired private NoSqlHelper noSqlHelper;

  @Autowired private SchemaManager schemaManager;

  @Autowired private ElasticsearchSchemaManager elasticsearchSchemaManager;

  private String originalSchemaContent;
  private IndexDescriptor indexDescriptor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @BeforeEach
  public void setUp() throws Exception {
    indexDescriptor = createIndexDescriptor();
    originalSchemaContent = readSchemaContent();
    assertThat(originalSchemaContent).doesNotContain("\"prop4\"");
  }

  @AfterEach
  public void tearDown() throws Exception {
    restoreOriginalSchemaContent();
    retryElasticsearchClient.deleteIndicesFor(getFullIndexName());
  }

  @Test
  public void shouldValidateDynamicIndexWithAddedProperty() throws Exception {
    replaceIndexDescriptorsInValidator(Collections.singleton(indexDescriptor));
    schemaManager.createIndex(indexDescriptor);

    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();

    createDocument(indexDescriptor, "prop0", "test");

    updateSchemaContent(
        originalSchemaContent.replace(
            "\"properties\":{", "\"properties\": {\"prop4\": {\"type\": \"keyword\" },"));

    final String newSchemaContent = readSchemaContent();
    assertThat(newSchemaContent).contains("\"prop4\"");

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

    createDocument(indexDescriptor, "prop0", "test");

    updateSchemaContent(originalSchemaContent.replace("{\"prop0\":{\"type\":\"keyword\"},", "{"));

    final String newSchemaContent = readSchemaContent();
    assertThat(newSchemaContent).doesNotContain("\"prop0\"");

    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();
  }

  @Test
  public void shouldSkipDifferenceOnDynamicField() throws Exception {
    replaceIndexDescriptorsInValidator(Collections.singleton(indexDescriptor));
    schemaManager.createIndex(indexDescriptor);

    var diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();

    /* Create document on the dynamic property to cause it to expand */
    createDocument(indexDescriptor, "prop2", Map.of("custom-key", "custom-value"));

    diff = indexSchemaValidator.validateIndexMappings();
    assertThat(diff).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnAmbiguousMappings() throws Exception {
    replaceIndexDescriptorsInValidator(Collections.singleton(indexDescriptor));

    final var datedDescriptor1 = createDatedIndexDescriptor("-2021-01-01");
    final var datedDescriptor2 = createDatedIndexDescriptor("-2021-01-02");
    try {
      schemaManager.createIndex(datedDescriptor1);
      createDocument(datedDescriptor1, Map.of("prop0", "test"));

      updateSchemaContent(
          originalSchemaContent.replace(
              "{\"prop0\":{\"type\":\"keyword\"},", "{\"prop0\":{\"type\":\"integer\"},"));
      schemaManager.createIndex(datedDescriptor2);
      createDocument(datedDescriptor2, Map.of("prop0", 123, "prop1", "test"));

      updateSchemaContent(
          originalSchemaContent.replace(
              "\"prop1\":{\"type\":\"keyword\"}", "\"prop1\":{\"type\":\"boolean\"}"));
      schemaManager.createIndex(indexDescriptor);
      createDocument(indexDescriptor, Map.of("prop0", 123, "prop1", true));

      restoreOriginalSchemaContent();

      assertThatThrownBy(() -> indexSchemaValidator.validateIndexMappings())
          .isInstanceOf(TasklistRuntimeException.class)
          .hasMessageContaining("Ambiguous schema update");
    } finally {
      retryElasticsearchClient.deleteIndicesFor(datedDescriptor1.getFullQualifiedName());
      retryElasticsearchClient.deleteIndicesFor(datedDescriptor2.getFullQualifiedName());
    }
  }

  private IndexDescriptor createIndexDescriptor() {
    return new AbstractIndexDescriptor("", true) {
      @Override
      public String getFullQualifiedName() {
        return getFullIndexName();
      }

      @Override
      public String getAlias() {
        return getFullQualifiedName() + "alias";
      }

      @Override
      public String getMappingsClasspathFilename() {
        return ORIGINAL_SCHEMA_PATH;
      }

      @Override
      public String getAllVersionsIndexNameRegexPattern() {
        return getFullIndexName() + ".*";
      }

      @Override
      public String getVersion() {
        return "1.0.0";
      }

      @Override
      public String getIndexPrefix() {
        return getIndexPrefixForTest();
      }

      @Override
      public String getComponentName() {
        return TASK_LIST.toString();
      }

      @Override
      public String getIndexName() {
        return INDEX_NAME;
      }
    };
  }

  private IndexDescriptor createDatedIndexDescriptor(final String suffix) {
    return new AbstractIndexDescriptor("", true) {
      @Override
      public String getFullQualifiedName() {
        return getFullIndexName() + suffix;
      }

      @Override
      public String getAlias() {
        return getFullIndexName() + "-alias";
      }

      @Override
      public String getMappingsClasspathFilename() {
        return ORIGINAL_SCHEMA_PATH;
      }

      @Override
      public String getAllVersionsIndexNameRegexPattern() {
        return getFullIndexName() + ".*";
      }

      @Override
      public String getVersion() {
        return "1.0.0";
      }

      @Override
      public String getIndexPrefix() {
        return getIndexPrefixForTest();
      }

      @Override
      public String getComponentName() {
        return TASK_LIST.toString();
      }

      @Override
      public String getIndexName() {
        return INDEX_NAME + suffix;
      }
    };
  }

  private String readSchemaContent() throws Exception {
    return new String(
            Files.readAllBytes(Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI())))
        .replaceAll("[\\n\\t\\s]+", "");
  }

  private void restoreOriginalSchemaContent() throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()),
        originalSchemaContent.getBytes(),
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  private void createDocument(
      final IndexDescriptor descriptor, final String key, final Object value) throws Exception {
    final Map<String, Object> document = Map.of(key, value);
    final boolean created =
        retryElasticsearchClient.createOrUpdateDocument(
            descriptor.getFullQualifiedName(), "id", document);
  }

  private void createDocument(final IndexDescriptor descriptor, final Map<String, Object> value)
      throws Exception {
    final boolean created =
        retryElasticsearchClient.createOrUpdateDocument(
            descriptor.getFullQualifiedName(), "id", value);
  }

  private void updateSchemaContent(final String content) throws Exception {
    Files.write(
        Paths.get(getClass().getResource(ORIGINAL_SCHEMA_PATH).toURI()),
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
    return getIndexPrefixForTest() + TASK_LIST + "-" + INDEX_NAME;
  }

  @NotNull
  private String getIndexPrefixForTest() {
    return AbstractIndexDescriptor.formatIndexPrefix(schemaManager.getIndexPrefix());
  }
}
