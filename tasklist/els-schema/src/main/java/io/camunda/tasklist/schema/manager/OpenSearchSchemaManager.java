/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexMapping;
import io.camunda.tasklist.schema.IndexMapping.IndexMappingProperty;
import io.camunda.tasklist.schema.indices.AbstractIndexDescriptor;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import io.camunda.zeebe.util.VisibleForTesting;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.util.EntityUtils;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.Alias;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest.Builder;
import org.opensearch.client.opensearch.indices.PutMappingRequest;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component("tasklistSchemaManager")
@Profile("!test")
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager implements SchemaManager {

  public static final String SETTINGS = "settings";
  public static final String MAPPINGS = "properties";
  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchSchemaManager.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected RetryOpenSearchClient retryOpenSearchClient;

  @Autowired
  @Qualifier("tasklistOsRestClient")
  private RestClient opensearchRestClient;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void createSchema() {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCyclesIfNotExist();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  @Override
  public IndexMapping getExpectedIndexFields(final IndexDescriptor indexDescriptor) {
    final InputStream description =
        OpenSearchSchemaManager.class.getResourceAsStream(
            indexDescriptor.getSchemaClasspathFilename());
    try {
      final String currentVersionSchema =
          StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      final TypeReference<HashMap<String, Object>> type = new TypeReference<>() {};
      final Map<String, Object> properties =
          (Map<String, Object>)
              objectMapper.readValue(currentVersionSchema, type).get("properties");
      final String dynamic =
          (String) objectMapper.readValue(currentVersionSchema, type).get("dynamic");
      return new IndexMapping()
          .setIndexName(indexDescriptor.getIndexName())
          .setDynamic(dynamic)
          .setProperties(
              properties.entrySet().stream()
                  .map(
                      entry ->
                          new IndexMappingProperty()
                              .setName(entry.getKey())
                              .setTypeDefinition(entry.getValue()))
                  .collect(Collectors.toSet()));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public Map<String, IndexMapping> getIndexMappings(final String indexNamePattern)
      throws IOException {
    final Map<String, IndexMapping> mappings = new HashMap<>();

    final Request request = new Request("GET", "/" + indexNamePattern + "/_mapping/");
    final Response response = opensearchRestClient.performRequest(request);
    final String responseBody = EntityUtils.toString(response.getEntity());

    // Initialize ObjectMapper instance
    final ObjectMapper objectMapper = new ObjectMapper();

    // Parse the JSON response body
    final Map<String, Map<String, Map<String, Object>>> parsedResponse =
        objectMapper.readValue(responseBody, new TypeReference<>() {});

    // Iterate over the parsed JSON to build the mappings
    for (final Map.Entry<String, Map<String, Map<String, Object>>> indexEntry :
        parsedResponse.entrySet()) {
      final String indexName = indexEntry.getKey();
      final Map<String, Object> indexMappingData = indexEntry.getValue().get("mappings");
      final String dynamicSetting = (String) indexMappingData.get("dynamic");

      // Extract the properties
      final Map<String, Object> propertiesData =
          (Map<String, Object>) indexMappingData.get("properties");
      final Set<IndexMapping.IndexMappingProperty> propertiesSet = new HashSet<>();

      for (final Map.Entry<String, Object> propertyEntry : propertiesData.entrySet()) {
        final IndexMapping.IndexMappingProperty property =
            new IndexMapping.IndexMappingProperty()
                .setName(propertyEntry.getKey())
                .setTypeDefinition(propertyEntry.getValue());
        propertiesSet.add(property);
      }

      // Create IndexMapping object
      final IndexMapping indexMapping =
          new IndexMapping()
              .setIndexName(indexName)
              .setDynamic(dynamicSetting)
              .setProperties(propertiesSet);

      // Add to mappings map
      mappings.put(indexName, indexMapping);
    }

    return mappings;
  }

  @Override
  public String getIndexPrefix() {
    return tasklistProperties.getOpenSearch().getIndexPrefix();
  }

  @Override
  public void updateSchema(final Map<IndexDescriptor, Set<IndexMappingProperty>> newFields) {
    for (final Map.Entry<IndexDescriptor, Set<IndexMappingProperty>> indexNewFields :
        newFields.entrySet()) {
      if (indexNewFields.getKey() instanceof final TemplateDescriptor templateDescriptor) {
        LOGGER.info("Update template: {}", templateDescriptor.getTemplateName());
        createTemplate(templateDescriptor, true);
      }

      final Map<String, Property> properties;
      try (final JsonParser jsonParser =
          JsonProvider.provider()
              .createParser(
                  new StringReader(
                      IndexMappingProperty.toJsonString(
                          indexNewFields.getValue(), objectMapper)))) {
        final JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
        properties =
            JsonpDeserializer.stringMapDeserializer(Property._DESERIALIZER)
                .deserialize(jsonParser, jsonpMapper);
      }
      final PutMappingRequest request =
          new PutMappingRequest.Builder()
              .index(indexNewFields.getKey().getAlias())
              .properties(properties)
              .build();
      LOGGER.info(
          String.format(
              "Index alias: %s. New fields will be added: %s",
              indexNewFields.getKey().getAlias(), indexNewFields.getValue()));
      retryOpenSearchClient.putMapping(request);
    }
  }

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename = indexDescriptor.getSchemaClasspathFilename();
    final InputStream indexDescription =
        OpenSearchSchemaManager.class.getResourceAsStream(indexFilename);

    final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
    final JsonParser parser = mapper.jsonProvider().createParser(indexDescription);

    final CreateIndexRequest request =
        new CreateIndexRequest.Builder()
            .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
            .aliases(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build())
            .settings(getIndexSettings(indexDescriptor.getIndexName()))
            .index(indexDescriptor.getFullQualifiedName())
            .build();

    createIndex(request, indexDescriptor.getFullQualifiedName());
  }

  private PutIndexTemplateRequest prepareIndexTemplateRequest(
      final TemplateDescriptor templateDescriptor, final String json) {
    final var templateSettings = templateSettings(templateDescriptor);
    final var templateBuilder =
        new IndexTemplateMapping.Builder()
            .aliases(templateDescriptor.getAlias(), new Alias.Builder().build());

    try {

      final var indexAsJSONNode = objectMapper.readTree(new StringReader(json));

      final var customSettings = getCustomSettings(templateSettings, indexAsJSONNode);
      final var mappings = getMappings(indexAsJSONNode.get(MAPPINGS));

      final IndexTemplateMapping template =
          templateBuilder.mappings(mappings).settings(customSettings).build();

      final PutIndexTemplateRequest request =
          new Builder()
              .name(templateDescriptor.getTemplateName())
              .indexPatterns(templateDescriptor.getIndexPattern())
              .template(template)
              .composedOf(settingsTemplateName())
              .build();
      return request;
    } catch (final Exception ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private TypeMapping getMappings(final JsonNode mappingsAsJSON) {
    final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();
    final JsonParser jsonParser =
        JsonProvider.provider().createParser(new StringReader(mappingsAsJSON.toPrettyString()));
    return TypeMapping._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
  }

  public void createIndexLifeCyclesIfNotExist() {
    if (retryOpenSearchClient.getLifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES).isPresent()) {
      LOGGER.info("{} ISM policy already exists", TASKLIST_DELETE_ARCHIVED_INDICES);
      return;
    }
    LOGGER.info("Creating ISM Policy for deleting archived indices");

    final Request request =
        new Request("PUT", "/_plugins/_ism/policies/" + TASKLIST_DELETE_ARCHIVED_INDICES);

    final JsonObject deleteJson =
        Json.createObjectBuilder().add("delete", Json.createObjectBuilder().build()).build();
    final JsonArray actionsDelete = Json.createArrayBuilder().add(deleteJson).build();
    final JsonObject deleteState =
        Json.createObjectBuilder()
            .add("name", Json.createValue("delete"))
            .add("actions", actionsDelete)
            .build();
    final JsonObject openCondition =
        Json.createObjectBuilder()
            .add(
                "min_index_age",
                Json.createValue(
                    tasklistProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices()))
            .build();
    final JsonObject openTransition =
        Json.createObjectBuilder()
            .add("state_name", Json.createValue("delete"))
            .add("conditions", openCondition)
            .build();
    final JsonArray transitionOpenActions = Json.createArrayBuilder().add(openTransition).build();
    final JsonObject openActionJson =
        Json.createObjectBuilder().add("open", Json.createObjectBuilder().build()).build();
    final JsonArray openActions = Json.createArrayBuilder().add(openActionJson).build();
    final JsonObject openState =
        Json.createObjectBuilder()
            .add("name", Json.createValue("open"))
            .add("actions", openActions)
            .add("transitions", transitionOpenActions)
            .build();
    final JsonArray statesJson = Json.createArrayBuilder().add(openState).add(deleteState).build();
    final JsonObject policyJson =
        Json.createObjectBuilder()
            .add("policy_id", Json.createValue(TASKLIST_DELETE_ARCHIVED_INDICES))
            .add(
                "description",
                Json.createValue("Policy to delete archived indices older than configuration"))
            .add("default_state", Json.createValue("open"))
            .add("states", statesJson)
            .build();
    final JsonObject requestJson = Json.createObjectBuilder().add("policy", policyJson).build();

    request.setJsonEntity(requestJson.toString());
    try {
      final Response response = opensearchRestClient.performRequest(request);
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  private void createDefaults() {
    final TasklistOpenSearchProperties elsConfig = tasklistProperties.getOpenSearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final IndexSettings settings = getDefaultIndexSettings();
    retryOpenSearchClient.createComponentTemplate(
        PutComponentTemplateRequest.of(
            b -> b.name(settingsTemplateName).template(t -> t.settings(settings))));
  }

  private IndexSettings getIndexSettings(final String indexName) {
    final var osConfig = tasklistProperties.getOpenSearch();
    final var shards =
        osConfig.getNumberOfShardsPerIndex().getOrDefault(indexName, osConfig.getNumberOfShards());
    final var replicas =
        osConfig
            .getNumberOfReplicasPerIndex()
            .getOrDefault(indexName, osConfig.getNumberOfReplicas());
    return IndexSettings.of(
        b -> b.numberOfShards(String.valueOf(shards)).numberOfReplicas(String.valueOf(replicas)));
  }

  private IndexSettings getDefaultIndexSettings() {
    final var osConfig = tasklistProperties.getOpenSearch();
    return IndexSettings.of(
        b ->
            b.numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
                .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas())));
  }

  private String settingsTemplateName() {
    final TasklistOpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    return String.format("%s_template", osConfig.getIndexPrefix());
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  @VisibleForTesting
  void createTemplate(final TemplateDescriptor templateDescriptor) {
    createTemplate(templateDescriptor, false);
  }

  private void createTemplate(
      final TemplateDescriptor templateDescriptor, final boolean overwrite) {
    final IndexTemplateMapping template = getTemplateFrom(templateDescriptor);

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .name(templateDescriptor.getTemplateName())
            .composedOf(List.of(settingsTemplateName()))
            .priority(tasklistProperties.getOpenSearch().getIndexTemplatePriority())
            .build(),
        overwrite);

    // This is necessary, otherwise tasklist won't find indexes at startup
    createIndex(templateDescriptor);
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request, final boolean overwrite) {
    final boolean created = retryOpenSearchClient.createTemplate(request, overwrite);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private IndexTemplateMapping getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename = templateDescriptor.getSchemaClasspathFilename();

    try (final InputStream templateConfig =
        OpenSearchSchemaManager.class.getResourceAsStream(templateFilename)) {

      final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
      final JsonParser parser = mapper.jsonProvider().createParser(templateConfig);

      return new IndexTemplateMapping.Builder()
          .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
          .aliases(templateDescriptor.getAlias(), new Alias.Builder().build())
          .build();
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Failed to load template file " + templateFilename + " from classpath", e);
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, final String indexName) {
    final boolean created = retryOpenSearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }

  private IndexSettings templateSettings(final TemplateDescriptor indexDescriptor) {
    final var shards =
        tasklistProperties
            .getOpenSearch()
            .getNumberOfShardsPerIndex()
            .get(indexDescriptor.getIndexName());

    final var replicas =
        tasklistProperties
            .getOpenSearch()
            .getNumberOfReplicasPerIndex()
            .get(indexDescriptor.getIndexName());

    if (shards != null || replicas != null) {
      final var indexSettingsBuilder = new IndexSettings.Builder();

      if (shards != null) {
        indexSettingsBuilder.numberOfShards(shards.toString());
      }

      if (replicas != null) {
        indexSettingsBuilder.numberOfReplicas(replicas.toString());
      }

      return indexSettingsBuilder.build();
    }
    return null;
  }

  private IndexSettings getCustomSettings(
      final IndexSettings defaultSettings, final JsonNode indexAsJSONNode) {
    final JsonbJsonpMapper jsonpMapper = new JsonbJsonpMapper();
    if (indexAsJSONNode.has(SETTINGS)) {
      final var settingsJSON = indexAsJSONNode.get(SETTINGS);
      final JsonParser jsonParser =
          JsonProvider.provider().createParser(new StringReader(settingsJSON.toPrettyString()));
      final var updatedSettings = IndexSettings._DESERIALIZER.deserialize(jsonParser, jsonpMapper);
      return new IndexSettings.Builder()
          .index(defaultSettings)
          .analysis(updatedSettings.analysis())
          .build();
    }
    return defaultSettings;
  }

  private static String readTemplateJson(final String classPathResourceName) {
    try {
      // read settings and mappings
      final InputStream description =
          OpenSearchSchemaManager.class.getResourceAsStream(classPathResourceName);
      final String json = StreamUtils.copyToString(description, StandardCharsets.UTF_8);
      return json;
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          "Exception occurred when reading template JSON: " + e.getMessage(), e);
    }
  }
}
