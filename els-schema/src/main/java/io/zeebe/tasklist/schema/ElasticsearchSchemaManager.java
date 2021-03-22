/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema;

import io.zeebe.tasklist.es.RetryElasticsearchClient;
import io.zeebe.tasklist.exceptions.MigrationException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.management.ElsIndicesCheck;
import io.zeebe.tasklist.property.MigrationProperties;
import io.zeebe.tasklist.property.TasklistElasticsearchProperties;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.schema.indices.AbstractIndexDescriptor;
import io.zeebe.tasklist.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.schema.migration.Migrator;
import io.zeebe.tasklist.schema.templates.TemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager {

  public static final int TEMPLATE_ORDER = 15;

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "number_of_replicas";
  private static final String ALIASES = "aliases";
  private static final String INDEX_PATTERNS = "index_patterns";

  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Autowired private ElsIndicesCheck elsIndicesCheck;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @PostConstruct
  public void initializeSchema() throws MigrationException {
    indexSchemaValidator.validate();
    if (tasklistProperties.getElasticsearch().isCreateSchema()
        && !indexSchemaValidator.schemaExists()) {
      LOGGER.info("Elasticsearch schema is empty or not complete. Indices will be created.");
      createSchema();
    } else {
      LOGGER.info(
          "Elasticsearch schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
    if (migrationProperties.isMigrationEnabled()) {
      final Migrator migrator = beanFactory.getBean(Migrator.class);
      migrator.migrate();
    }
  }

  public void createSchema() {
    createDefaults();
    createTemplates();
    createIndices();
  }

  private void createDefaults() {
    final TasklistElasticsearchProperties elsConfig = tasklistProperties.getElasticsearch();
    final String settingsTemplate = String.format("%s_template", elsConfig.getIndexPrefix());

    final List<String> patterns = List.of(String.format("%s-*", elsConfig.getIndexPrefix()));
    LOGGER.info(
        "Create default settings from '{}' with {} shards and {} replicas per index.",
        settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final PutIndexTemplateRequest putIndexTemplateRequest =
        new PutIndexTemplateRequest(settingsTemplate)
            .order(TEMPLATE_ORDER) // order of template applying
            .patterns(patterns)
            .settings(
                Settings.builder()
                    .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
                    .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
                    .build());

    putIndexTemplate(putIndexTemplateRequest, settingsTemplate);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format("/schema/create/index/tasklist-%s.json", indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription =
        prepareCreateIndex(indexFilename, indexDescriptor.getAlias());
    createIndex(
        new CreateIndexRequest(indexDescriptor.getFullQualifiedName()).source(indexDescription),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format(
            "/schema/create/template/tasklist-%s.json", templateDescriptor.getIndexName());
    final Map<String, Object> template = readJSONFileToMap(templateFilename);
    // Adjust prefixes and aliases in case of other configured indexNames, e.g. non-default prefix
    template.put(INDEX_PATTERNS, Collections.singletonList(templateDescriptor.getIndexPattern()));
    template.put(
        ALIASES, Collections.singletonMap(templateDescriptor.getAlias(), Collections.emptyMap()));
    putIndexTemplate(
        new PutIndexTemplateRequest(templateDescriptor.getTemplateName()).source(template),
        templateDescriptor.getTemplateName());
    // This is necessary, otherwise Tasklist won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest(indexName), indexName);
  }

  private Map<String, Object> prepareCreateIndex(String fileName, String alias) {
    final Map<String, Object> indexDescription = readJSONFileToMap(fileName);
    // Adjust aliases in case of other configured indexNames, e.g. non-default prefix
    indexDescription.put(ALIASES, Collections.singletonMap(alias, Collections.emptyMap()));
    return indexDescription;
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    final boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(
      final PutIndexTemplateRequest putIndexTemplateRequest, String templateName) {
    final boolean created = retryElasticsearchClient.createTemplate(putIndexTemplateRequest);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", templateName);
    } else {
      LOGGER.debug("Template [{}] was NOT created", templateName);
    }
  }
}
