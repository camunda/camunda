/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.manager;

import static io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor.formatIndexPrefix;
import static io.camunda.webapps.schema.descriptors.ComponentNames.TASK_LIST;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
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
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
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

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;
  @Autowired private List<IndexTemplateDescriptor> templateDescriptors;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void createSchema() {
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
      final Map<String, Object> mappings =
          (Map<String, Object>) objectMapper.readValue(currentVersionSchema, type).get("mappings");
      final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
      final String dynamic = (String) mappings.get("dynamic");
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

    final Map<String, TypeMapping> indexMappings =
        openSearchClient
            .indices()
            .getMapping(req -> req.index(indexNamePattern).ignoreUnavailable(true))
            .result()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().mappings()));

    // Iterate over the parsed JSON to build the mappings
    for (final Entry<String, TypeMapping> indexEntry : indexMappings.entrySet()) {
      final String indexName = indexEntry.getKey();
      final Map<String, Property> indexMappingData = indexEntry.getValue().properties();
      final String dynamic =
          indexEntry.getValue().dynamic() == null
              ? "strict"
              : indexEntry.getValue().dynamic().toString().toLowerCase();

      final Set<IndexMapping.IndexMappingProperty> propertiesSet = new HashSet<>();

      for (final Map.Entry<String, Property> propertyEntry : indexMappingData.entrySet()) {
        final IndexMapping.IndexMappingProperty property =
            new IndexMapping.IndexMappingProperty()
                .setName(propertyEntry.getKey())
                .setTypeDefinition(propertyToMap(propertyEntry.getValue()));
        propertiesSet.add(property);
      }

      // Create IndexMapping object
      final IndexMapping indexMapping =
          new IndexMapping()
              .setIndexName(indexName)
              .setDynamic(dynamic)
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
            .mappings(IndexTemplateMapping._DESERIALIZER.deserialize(parser, mapper).mappings())
            .aliases(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build())
            .settings(getIndexSettings())
            .index(indexDescriptor.getFullQualifiedName())
            .build();

    createIndex(request, indexDescriptor.getFullQualifiedName());
  }

  private PutIndexTemplateRequest prepareIndexTemplateRequest(
      final IndexTemplateDescriptor templateDescriptor, final String json) {
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

  private void createDefaults() {
    final TasklistOpenSearchProperties elsConfig = tasklistProperties.getOpenSearch();

    final String settingsTemplateName = settingsTemplateName();
    LOGGER.info(
        "Create default settings '{}' with {} shards and {} replicas per index.",
        settingsTemplateName,
        elsConfig.getNumberOfShards(),
        elsConfig.getNumberOfReplicas());

    final IndexSettings settings = getIndexSettings();
    retryOpenSearchClient.createComponentTemplate(
        new PutComponentTemplateRequest.Builder()
            .name(settingsTemplateName)
            // .settings(settings)
            .template(t -> t.settings(settings))
            .build());
  }

  private IndexSettings getIndexSettings() {
    final TasklistOpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    return new IndexSettings.Builder()
        .numberOfShards(String.valueOf(osConfig.getNumberOfShards()))
        .numberOfReplicas(String.valueOf(osConfig.getNumberOfReplicas()))
        .build();
  }

  private String settingsTemplateName() {
    final TasklistOpenSearchProperties osConfig = tasklistProperties.getOpenSearch();
    return String.format("%s%s_template", formatIndexPrefix(osConfig.getIndexPrefix()), TASK_LIST);
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

<<<<<<< HEAD
  private void createTemplate(final IndexTemplateDescriptor templateDescriptor) {
=======
  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    createTemplate(templateDescriptor, false);
  }

  private void createTemplate(
      final TemplateDescriptor templateDescriptor, final boolean overwrite) {
>>>>>>> 77e0f2d6cba (fix: Tasklist Opensearch schema manager does not update index templates mappings)
    final IndexTemplateMapping template = getTemplateFrom(templateDescriptor);

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .name(templateDescriptor.getTemplateName())
            .composedOf(List.of(settingsTemplateName()))
            .build(),
        overwrite);

    // This is necessary, otherwise tasklist won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest.Builder().index(indexName).build(), indexName);
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request, final boolean overwrite) {
    final boolean created = retryOpenSearchClient.createTemplate(request, overwrite);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

<<<<<<< HEAD
  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    final boolean created = retryOpenSearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private IndexTemplateMapping getTemplateFrom(final IndexTemplateDescriptor templateDescriptor) {
=======
  private IndexTemplateMapping getTemplateFrom(final TemplateDescriptor templateDescriptor) {
>>>>>>> 77e0f2d6cba (fix: Tasklist Opensearch schema manager does not update index templates mappings)
    final String templateFilename = templateDescriptor.getSchemaClasspathFilename();

    try (final InputStream templateConfig =
        OpenSearchSchemaManager.class.getResourceAsStream(templateFilename)) {

      final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
      final JsonParser parser = mapper.jsonProvider().createParser(templateConfig);

<<<<<<< HEAD
    return new IndexTemplateMapping.Builder()
        .mappings(IndexTemplateMapping._DESERIALIZER.deserialize(parser, mapper).mappings())
        .aliases(templateDescriptor.getAlias(), new Alias.Builder().build())
        .build();
=======
      return new IndexTemplateMapping.Builder()
          .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
          .aliases(templateDescriptor.getAlias(), new Alias.Builder().build())
          .build();
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Failed to load template file " + templateFilename + " from classpath", e);
    }
>>>>>>> 77e0f2d6cba (fix: Tasklist Opensearch schema manager does not update index templates mappings)
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

  private IndexSettings templateSettings(final IndexTemplateDescriptor indexDescriptor) {
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

  // Ported from CamundaExporter
  private Map<String, Object> serialize(
      final Function<JsonGenerator, jakarta.json.stream.JsonGenerator> jacksonGenerator,
      final Consumer<jakarta.json.stream.JsonGenerator> serialize)
      throws IOException {
    try (final var out = new StringWriter();
        final var jsonGenerator = new JsonFactory().createGenerator(out);
        final jakarta.json.stream.JsonGenerator jacksonJsonpGenerator =
            jacksonGenerator.apply(jsonGenerator)) {
      serialize.accept(jacksonJsonpGenerator);
      jacksonJsonpGenerator.flush();

      return objectMapper.readValue(
          out.toString(), new TypeReference<TreeMap<String, Object>>() {});
    }
  }

  // Ported from CamundaExporter
  private Map<String, Object> propertyToMap(final Property property) {
    try {
      return serialize(
          (JacksonJsonpGenerator::new),
          (jacksonJsonpGenerator) ->
              property.serialize(jacksonJsonpGenerator, new JacksonJsonpMapper(objectMapper)));
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format("Failed to serialize property [%s]", property.toString()), e);
    }
  }
}
