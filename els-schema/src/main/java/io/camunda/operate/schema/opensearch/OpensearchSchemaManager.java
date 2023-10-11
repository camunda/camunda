/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.AbstractIndexDescriptor;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Component("schemaManager")
@Profile("!test")
@Conditional(OpensearchCondition.class)
public class OpensearchSchemaManager implements SchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchSchemaManager.class);

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchSchemaManager.class);
  public static final String SCHEMA_OPENSEARCH_CREATE_TEMPLATE_JSON = "/schema/opensearch/create/template/operate-%s.json";
  public static final String SCHEMA_OPENSEARCH_CREATE_INDEX_JSON = "/schema/opensearch/create/index/operate-%s.json";

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired private OpenSearchClient openSearchClient;

  @Override
  public void createSchema() {
    /*if (operateProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }*/
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public boolean setIndexSettingsFor(Map<String, ?> settings, String indexPattern) {
    IndexSettings indexSettings =  new IndexSettings.Builder()
        .refreshInterval( ri -> ri.time(((String)settings.get(REFRESH_INTERVAL))))
        .numberOfReplicas(String.valueOf(settings.get(NUMBERS_OF_REPLICA)))
        .build();
    return richOpenSearchClient.index().setIndexSettingsFor(indexSettings, indexPattern);
  }

  @Override
  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultRefreshInterval(indexName, defaultValue);
  }

  @Override
  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    return richOpenSearchClient.index().getOrDefaultNumbersOfReplica(indexName, defaultValue);
  }

  @Override
  public void refresh(String indexPattern) {
    richOpenSearchClient.index().refreshWithRetries(indexPattern);
  }

  @Override
  public boolean isHealthy() {
    return richOpenSearchClient.cluster().isHealthy();
  }

  @Override
  public Set<String> getIndexNames(String indexPattern) {
    return richOpenSearchClient.index().getIndexNamesWithRetries(indexPattern);
  }

  @Override
  public long getNumberOfDocumentsFor(String... indexPatterns) {
    return richOpenSearchClient.index().getNumberOfDocumentsWithRetries(indexPatterns);
  }

  @Override
  public boolean deleteIndicesFor(String indexPattern) {
    return richOpenSearchClient.index().deleteIndicesWithRetries(indexPattern);
  }

  @Override
  public boolean deleteTemplatesFor(String deleteTemplatePattern) {
    return richOpenSearchClient.template().deleteTemplatesWithRetries(deleteTemplatePattern);
  }

  @Override
  public void removePipeline(String pipelineName) {
    richOpenSearchClient.pipeline().removePipelineWithRetries(pipelineName);
  }

  @Override
  public boolean addPipeline(String name, String pipelineDefinition) {
    return richOpenSearchClient.pipeline().addPipelineWithRetries(name, pipelineDefinition);
  }

  @Override
  public Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return richOpenSearchClient.index().getIndexSettingsWithRetries(indexName, fields);
  }


  private void createDefaults() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        osConfig.getNumberOfShards(),
        osConfig.getNumberOfReplicas());

    final IndexSettings settings = getIndexSettings();
    richOpenSearchClient.template().createComponentTemplateWithRetries(
        new PutComponentTemplateRequest.Builder()
            .name(settingsTemplateName)
            .template(t -> t.settings(settings))
            .build());
  }

  private IndexSettings getIndexSettings() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
        .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas()))
        .build();
  }

  private String settingsTemplateName() {
    final OperateOpensearchProperties osConfig = operateProperties.getOpensearch();
    return String.format("%s_template", osConfig.getIndexPrefix());
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {

    final IndexTemplateMapping template = new IndexTemplateMapping.Builder()
        .aliases(templateDescriptor.getAlias(), new Alias.Builder().build()).build();

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .name(templateDescriptor.getTemplateName())
            .indexPatterns(templateDescriptor.getIndexPattern())
            .template(template)
            .composedOf(settingsTemplateName())
            .build());

    // This is necessary, otherwise operate won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    final String templateFileName = String.format(SCHEMA_OPENSEARCH_CREATE_TEMPLATE_JSON, templateDescriptor.getIndexName());
    try {
      final InputStream description = OpensearchSchemaManager.class.getResourceAsStream(templateFileName);
      var request = createIndexFromJson(
          StreamUtils.copyToString(description, Charset.defaultCharset()),
          templateDescriptor.getFullQualifiedName(),
          Map.of(templateDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
          getIndexSettings()
      );
      createIndex(request, indexName);
    }catch (Exception e){
      throw new OperateRuntimeException(e);
    }
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    final boolean created = richOpenSearchClient.template().createTemplateWithRetries(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    final boolean created = richOpenSearchClient.index().createIndexWithRetries(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndex(final IndexDescriptor indexDescriptor)  {
    try {
      final String indexFilename = String.format(SCHEMA_OPENSEARCH_CREATE_INDEX_JSON, indexDescriptor.getIndexName());
      final InputStream description = OpensearchSchemaManager.class.getResourceAsStream(indexFilename);
      var request = createIndexFromJson(
          StreamUtils.copyToString(description, Charset.defaultCharset()),
          indexDescriptor.getFullQualifiedName(),
          Map.of(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build()),
          getIndexSettings()
      );
      createIndex(request, indexDescriptor.getFullQualifiedName());
    }catch (Exception e){
      throw new OperateRuntimeException("Could not create index "+indexDescriptor.getIndexName(), e);
    }
  }

  // Needed for loading from JSON, opensearch doesn't provide something like .withJSON(...) for index creation from files
  private CreateIndexRequest createIndexFromJson(String json, String indexName, Map<String, Alias> aliases, IndexSettings settings) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    JsonParser jsonParser = JsonProvider.provider().createParser(new StringReader(json)); // <-- your JSON request body
    Supplier<CreateIndexRequest.Builder> builderSupplier = () -> new CreateIndexRequest.Builder()
        .index(indexName)
        .aliases(aliases)
        .settings(settings);
    ObjectDeserializer<CreateIndexRequest.Builder> deserializer = getDeserializerWithPreconfiguredBuilder(builderSupplier);
    try {
      return deserializer.deserialize(jsonParser, new JsonbJsonpMapper()).build();
    } catch (Exception e){
      throw new OperateRuntimeException("Could not load schema for " + indexName, e);
    }
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private static ObjectDeserializer<CreateIndexRequest.Builder> getDeserializerWithPreconfiguredBuilder(
      Supplier<CreateIndexRequest.Builder> builderSupplier) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Class<CreateIndexRequest> clazz = CreateIndexRequest.class;
    Method method = clazz.getDeclaredMethod("setupCreateIndexRequestDeserializer", ObjectDeserializer.class);
    method.setAccessible(true);

    ObjectDeserializer<CreateIndexRequest.Builder> deserializer = new ObjectDeserializer<>(builderSupplier);
    method.invoke(null, deserializer);
    return deserializer;
  }

  @Override
  public String getIndexPrefix() {
    return operateProperties.getOpensearch().getIndexPrefix();
  }
}
