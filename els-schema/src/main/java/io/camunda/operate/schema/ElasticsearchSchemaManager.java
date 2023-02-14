/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Profile("!test")
public class ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSchemaManager.class);

  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";
  private static final String ALIASES = "aliases";

  @Autowired
  private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  @Autowired
  protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  protected OperateProperties operateProperties;

  public void createSchema() {
    createDefaults();
    createTemplates();
    createIndices();
  }

  private String settingsTemplateName() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return String.format("%s_template", elsConfig.getIndexPrefix());
  }

  private Settings getIndexSettings() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    return Settings.builder()
        .put(NUMBER_OF_SHARDS, elsConfig.getNumberOfShards())
        .put(NUMBER_OF_REPLICAS, elsConfig.getNumberOfReplicas())
        .build();
  }

  private void createDefaults() {
    final OperateElasticsearchProperties elsConfig = operateProperties.getElasticsearch();
    final String settingsTemplate = settingsTemplateName();
    logger.info("Create default settings from '{}' with {} shards and {} replicas per index.", settingsTemplate,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    Settings settings = getIndexSettings();

    final Template template = new Template(settings, null, null);
    final ComponentTemplate componentTemplate = new ComponentTemplate(template, null, null);
    final PutComponentTemplateRequest request = new PutComponentTemplateRequest()
        .name(settingsTemplate)
        .componentTemplate(componentTemplate);
    retryElasticsearchClient.createComponentTemplate(request);
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename = String.format("/schema/create/index/operate-%s.json",
        indexDescriptor.getIndexName());
    final Map<String, Object> indexDescription = readJSONFileToMap(indexFilename);
    createIndex(new CreateIndexRequest(indexDescriptor.getFullQualifiedName())
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexDescriptor.getAlias()).writeIndex(false)))
            .settings(getIndexSettings()),
        indexDescriptor.getFullQualifiedName());
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    Template template = getTemplateFrom(templateDescriptor);
    ComposableIndexTemplate composableTemplate = new ComposableIndexTemplate.Builder()
        .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
        .template(template)
        .componentTemplates(List.of(settingsTemplateName()))
        .build();
    putIndexTemplate(new PutComposableIndexTemplateRequest()
        .name(templateDescriptor.getTemplateName())
        .indexTemplate(composableTemplate));
    // This is necessary, otherwise operate won't find indexes at startup
    String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest(indexName), indexName);
  }

  private Template getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename = String.format("/schema/create/template/operate-%s.json", templateDescriptor
        .getIndexName());
    // Easiest way to create Template from json file: create 'old' request ang retrieve needed info
    final Map<String, Object> templateConfig = readJSONFileToMap(templateFilename);
    PutIndexTemplateRequest ptr = new PutIndexTemplateRequest(templateDescriptor.getTemplateName())
        .source(templateConfig);
    try {
      final Map<String, AliasMetadata> aliases = Map.of(
          templateDescriptor.getAlias(),AliasMetadata.builder(templateDescriptor.getAlias()).build());
      return new Template(ptr.settings(), new CompressedXContent(ptr.mappings()), aliases);
    } catch (IOException e) {
      throw new OperateRuntimeException(
          String.format("Error in reading mappings for %s ",templateDescriptor.getTemplateName()), e );
    }
  }

  private Map<String, Object> readJSONFileToMap(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = ElasticsearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        result = XContentHelper.convertToMap(XContentType.JSON.xContent(), inputStream, true);
      } else {
        throw new OperateRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new OperateRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
    return result;
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    boolean created = retryElasticsearchClient.createIndex(createIndexRequest);
    if (created) {
      logger.debug("Index [{}] was successfully created", indexName);
    } else {
      logger.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void putIndexTemplate(final PutComposableIndexTemplateRequest request) {
    boolean created = retryElasticsearchClient.createTemplate(request);
    if (created) {
      logger.debug("Template [{}] was successfully created", request.name());
    } else {
      logger.debug("Template [{}] was NOT created", request.name());
    }
  }

}
