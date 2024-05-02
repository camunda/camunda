/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.IndexMapping.IndexMappingProperty;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.schema.util.ObservableConnector;
import io.camunda.operate.schema.util.ObservableConnector.OperateTestHttpRequest;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.elasticsearch.TestElasticsearchConnector;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.TestOpenSearchConnector;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      IndexSchemaValidator.class,
      TestIndex.class,
      TestTemplate.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchSchemaTestHelper.class,
      ElasticsearchTaskStore.class,
      TestElasticsearchConnector.class,
      OpensearchSchemaManager.class,
      OpenSearchSchemaTestHelper.class,
      OpensearchTaskStore.class,
      TestOpenSearchConnector.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class
    })
public class SchemaManagerIT extends AbstractSchemaIT {

  @Autowired public IndexSchemaValidator validator;

  @Autowired public SchemaManager schemaManager;
  @Autowired public SchemaTestHelper schemaHelper;

  @Autowired public TestIndex testIndex;
  @Autowired public TestTemplate testTemplate;

  @Autowired public ObservableConnector searchConnector;

  @BeforeEach
  public void createDefault() {
    schemaManager.createDefaults();
  }

  @AfterEach
  public void tearDown() {
    searchConnector.clearRequestListeners();
    schemaHelper.dropSchema();
  }

  @Test
  public void shouldAddFieldToIndex() {
    // given
    // a schema that has a missing field
    schemaManager.createIndex(
        testIndex, "/schema/elasticsearch/create/index/operate-testindex-property-removed.json");
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
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
  public void shouldAddFieldToTemplate() {
    // given
    // a schema that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");
    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
    final IndexMapping templateMappings = schemaHelper.getTemplateMappings(testTemplate);
    assertThat(templateMappings).isNotNull();

    assertThat(templateMappings.getProperties())
        .containsOnly(
            new IndexMappingProperty()
                .setName("propA")
                .setTypeDefinition(Map.of("type", "keyword")),
            new IndexMappingProperty()
                .setName("propB")
                .setTypeDefinition(Map.of("type", "keyword")),
            new IndexMappingProperty()
                .setName("propC")
                .setTypeDefinition(Map.of("type", "keyword")));

    final String indexName = testTemplate.getFullQualifiedName();
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
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propA")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propC")
                                .setTypeDefinition(Map.of("type", "keyword"))))));
  }

  @Test
  public void shouldAddFieldsToAllIndexesOfATemplate() {
    // given
    // a schema that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");

    // and a second templated index of it
    final String secondTemplatedIndexName = testTemplate.getFullQualifiedName() + "instantiated";
    final Map<String, Object> document = Map.of("propA", "test", "propB", "test");
    clientTestHelper.createDocument(secondTemplatedIndexName, "1", document);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // when
    schemaManager.updateSchema(indexDiff);

    // then
    // the changes were also applied to the templated index
    final Map<String, IndexMapping> indexMappings =
        schemaManager.getIndexMappings(secondTemplatedIndexName);

    assertThat(indexMappings)
        .containsExactly(
            entry(
                secondTemplatedIndexName,
                new IndexMapping()
                    .setIndexName(secondTemplatedIndexName)
                    .setDynamic("strict")
                    .setProperties(
                        Set.of(
                            new IndexMappingProperty()
                                .setName("propB")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propA")
                                .setTypeDefinition(Map.of("type", "keyword")),
                            new IndexMappingProperty()
                                .setName("propC")
                                .setTypeDefinition(Map.of("type", "keyword"))))));
  }

  @Test
  public void shouldRetryIfFieldCannotBeAddedToAllIndexes() {
    // the OpenSearch client uses a high number of retries on failures, currently not configurable
    failIfOpensearch();

    // given
    // a schema that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");

    // and a second templated index of it
    final String secondTemplatedIndexName = testTemplate.getFullQualifiedName() + "instantiated";
    final Map<String, Object> document = Map.of("propA", "test", "propB", "test");
    clientTestHelper.createDocument(secondTemplatedIndexName, "1", document);

    // and one of the indices is read only
    schemaHelper.setReadOnly(secondTemplatedIndexName, true);

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    final AtomicInteger numberOfPutIndexRequests = new AtomicInteger();

    searchConnector.addRequestListener(
        httpRequest -> {
          if (isIndexTemplatePutRequest(httpRequest, testTemplate)) {
            numberOfPutIndexRequests.incrementAndGet();
          }
        });

    // when
    try {
      schemaManager.updateSchema(indexDiff);
    } catch (final OperateRuntimeException e) {
      // expected
    }

    // then
    assertThat(numberOfPutIndexRequests).hasValue(REQUEST_RETRIES);
  }

  /**
   * By updating a template before its indexes, we ensure that any new indexes created from the
   * template are already up to date.
   */
  @Test
  public void shouldUpdateTemplatesFirst() {
    // the OpenSearch client uses a high number of retries on failures, currently not configurable
    failIfOpensearch();

    // given
    // a template that has a missing field
    schemaManager.createTemplate(
        testTemplate,
        "/schema/elasticsearch/create/template/operate-testtemplate-property-removed.json");

    final Map<IndexDescriptor, Set<IndexMappingProperty>> indexDiff =
        validator.validateIndexMappings();

    // and the corresponding index cannot be updated
    final String indexName = testTemplate.getFullQualifiedName();
    schemaHelper.setReadOnly(indexName, true);

    // when
    try {
      schemaManager.updateSchema(indexDiff);
    } catch (final OperateRuntimeException e) {
      // expected
    }

    // then
    final IndexMapping templateMappings = schemaHelper.getTemplateMappings(testTemplate);
    final Set<IndexMappingProperty> properties = templateMappings.getProperties();
    assertThat(properties).extracting("name").containsOnly("propA", "propB", "propC");
  }

  protected static boolean isIndexTemplatePutRequest(
      final OperateTestHttpRequest request, final TemplateDescriptor descriptor) {

    final String expectedUri = "/" + descriptor.getAlias() + "/_mapping";
    final URI actualUri;
    try {
      actualUri = new URI(request.getUri());
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }

    return "PUT".equals(request.getMethod()) && expectedUri.equals(actualUri.getPath());
  }
}
