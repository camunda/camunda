/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.manager;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.AbstractIndexDescriptor;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.schema.templates.TemplateDescriptor;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
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

@Component("schemaManager")
@Profile("!test")
@Conditional(OpenSearchCondition.class)
public class OpenSearchSchemaManager implements SchemaManager {

  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchSchemaManager.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired protected RetryOpenSearchClient retryOpenSearchClient;

  @Autowired protected RestClient opensearchRestClient;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  @Autowired private List<AbstractIndexDescriptor> indexDescriptors;

  @Autowired private OpenSearchClient openSearchClient;

  @Override
  public void createSchema() {
    if (tasklistProperties.getArchiver().isIlmEnabled()) {
      createIndexLifeCycles();
    }
    createDefaults();
    createTemplates();
    createIndices();
  }

  private void createIndexLifeCycles() {
    LOGGER.warn("ISM is not implemented in Opensearch Java client");

    final Request request =
        new Request("PUT", "_plugins/_ism/policies/" + TASKLIST_DELETE_ARCHIVED_INDICES);
    final JSONObject requestJson = new JSONObject();
    final JSONArray statesJson = new JSONArray();
    final JSONObject openState = new JSONObject();
    final JSONArray openActions = new JSONArray();
    final JSONObject openActionJson = new JSONObject();
    final JSONArray transitionOpenActions = new JSONArray();
    final JSONObject openTransition = new JSONObject();
    final JSONObject openCondition = new JSONObject();
    final JSONObject deleteState = new JSONObject();
    final JSONArray actionsDelete = new JSONArray();
    final JSONObject deleteJson = new JSONObject();
    deleteJson.put("delete", new JSONObject());
    actionsDelete.put(deleteJson);
    deleteState.put("name", "delete");
    deleteState.put("actions", actionsDelete);
    openCondition.put(
        "min_index_age", tasklistProperties.getArchiver().getIlmMinAgeForDeleteArchivedIndices());
    openTransition.put("state_name", "delete");
    openTransition.put("conditions", openCondition);
    openActionJson.put("open", new JSONObject());
    openActions.put(openActionJson);
    openState.put("name", "open");
    openState.put("actions", openActions);
    transitionOpenActions.put(openTransition);
    openState.put("transitions", transitionOpenActions);
    statesJson.put(openState);
    statesJson.put(deleteState);
    final JSONObject policyJson = new JSONObject();
    policyJson.put("policy_id", TASKLIST_DELETE_ARCHIVED_INDICES);
    policyJson.put("description", "Policy to delete archived indices older than configuration");
    policyJson.put("default_state", "open");
    policyJson.put("states", statesJson);

    requestJson.put("policy", policyJson);

    request.setJsonEntity(requestJson.toString());
    try {
      final Response response = opensearchRestClient.performRequest(request);
    } catch (IOException e) {
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
    return String.format("%s_template", osConfig.getIndexPrefix());
  }

  private void createTemplates() {
    templateDescriptors.forEach(this::createTemplate);
  }

  private void createTemplate(final TemplateDescriptor templateDescriptor) {
    final IndexTemplateMapping template = getTemplateFrom(templateDescriptor);

    putIndexTemplate(
        new PutIndexTemplateRequest.Builder()
            .indexPatterns(List.of(templateDescriptor.getIndexPattern()))
            .template(template)
            .name(templateDescriptor.getTemplateName())
            .composedOf(List.of(settingsTemplateName()))
            .build());

    // This is necessary, otherwise tasklist won't find indexes at startup
    final String indexName = templateDescriptor.getFullQualifiedName();
    createIndex(new CreateIndexRequest.Builder().index(indexName).build(), indexName);
  }

  private void putIndexTemplate(final PutIndexTemplateRequest request) {
    final boolean created = retryOpenSearchClient.createTemplate(request);
    if (created) {
      LOGGER.debug("Template [{}] was successfully created", request.name());
    } else {
      LOGGER.debug("Template [{}] was NOT created", request.name());
    }
  }

  private IndexTemplateMapping getTemplateFrom(final TemplateDescriptor templateDescriptor) {
    final String templateFilename =
        String.format(
            "/schema/os/create/template/tasklist-%s.json", templateDescriptor.getIndexName());

    final InputStream templateConfig =
        OpenSearchSchemaManager.class.getResourceAsStream(templateFilename);

    final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
    final JsonParser parser = mapper.jsonProvider().createParser(templateConfig);

    return new IndexTemplateMapping.Builder()
        .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
        .aliases(templateDescriptor.getAlias(), new Alias.Builder().build())
        .build();
  }

  private InputStream readJSONFile(final String filename) {
    final Map<String, Object> result;
    try (InputStream inputStream = OpenSearchSchemaManager.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        return inputStream;
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
  }

  private void createIndex(final CreateIndexRequest createIndexRequest, String indexName) {
    final boolean created = retryOpenSearchClient.createIndex(createIndexRequest);
    if (created) {
      LOGGER.debug("Index [{}] was successfully created", indexName);
    } else {
      LOGGER.debug("Index [{}] was NOT created", indexName);
    }
  }

  private void createIndex(final IndexDescriptor indexDescriptor) {
    final String indexFilename =
        String.format("/schema/os/create/index/tasklist-%s.json", indexDescriptor.getIndexName());
    final InputStream indexDescription =
        OpenSearchSchemaManager.class.getResourceAsStream(indexFilename);

    final JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
    final JsonParser parser = mapper.jsonProvider().createParser(indexDescription);

    final CreateIndexRequest request =
        new CreateIndexRequest.Builder()
            .mappings(TypeMapping._DESERIALIZER.deserialize(parser, mapper))
            .aliases(indexDescriptor.getAlias(), new Alias.Builder().isWriteIndex(false).build())
            .settings(getIndexSettings())
            .index(indexDescriptor.getFullQualifiedName())
            .build();

    createIndex(request, indexDescriptor.getFullQualifiedName());
  }

  private void createIndices() {
    indexDescriptors.forEach(this::createIndex);
  }
}
