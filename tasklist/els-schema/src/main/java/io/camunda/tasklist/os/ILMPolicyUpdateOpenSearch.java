/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.es.ILMPolicyUpdateElasticSearch;
import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.opensearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ILMPolicyUpdateOpenSearch implements ILMPolicyUpdate {

  private static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(ILMPolicyUpdateElasticSearch.class);

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Autowired private OpenSearchSchemaManager schemaManager;

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void applyIlmPolicyToAllIndices() throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getOpenSearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";
    LOGGER.info("Applying ISM policy to all existent indices");
    final Response policyExists =
        retryOpenSearchClient.getLifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES);
    if (policyExists == null) {
      LOGGER.info("ISM policy does not exist, creating it");
      schemaManager.createIndexLifeCycles();
    }
    applyIlmPolicyToIndexTemplate(true);
    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);

    final Set<String> response = retryOpenSearchClient.getIndexNames(taskListIndexWildCard);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, TASKLIST_DELETE_ARCHIVED_INDICES);
      }
    }
  }

  @Override
  public void removeIlmPolicyFromAllIndices() throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getOpenSearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";

    LOGGER.info("Removing ISM policy to all existent indices");
    final Set<String> response = retryOpenSearchClient.getIndexNames(taskListIndexWildCard);
    applyIlmPolicyToIndexTemplate(false);
    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, null);
      }
    }
  }

  private void applyIlmPolicyToIndexTemplate(final boolean applyPolicy) throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final JsonArray templates =
        retryOpenSearchClient.getIndexTemplateSettings(taskListIndexWildCard);
    // Integration tests are not creating the templates, so we need to check if they exist
    if (templates != null) {
      for (final JsonObject templateData : templates.getValuesAs(JsonObject.class)) {
        final String templateName = templateData.getString("name");
        final JsonObject template = templateData.getJsonObject("index_template");

        final JsonArray indexPatterns = template.getJsonArray("index_patterns");

        final JsonObject innerTemplate = template.getJsonObject("template");

        final JsonObject existingSettings =
            (innerTemplate != null && innerTemplate.containsKey("settings"))
                ? innerTemplate.getJsonObject("settings")
                : Json.createObjectBuilder().build();

        final JsonObjectBuilder settingsBuilder = Json.createObjectBuilder();

        if (existingSettings != null) {
          for (final String key : existingSettings.keySet()) {
            settingsBuilder.add(key, existingSettings.get(key));
          }

          if (applyPolicy) {
            settingsBuilder.add(
                "plugins.index_state_management.policy_id", TASKLIST_DELETE_ARCHIVED_INDICES);
          } else {
            settingsBuilder.add("plugins.index_state_management.policy_id", JsonObject.NULL);
          }

          final String requiredPolicyId = applyPolicy ? TASKLIST_DELETE_ARCHIVED_INDICES : null;

          if (isPolicyAlreadyApplied(existingSettings, requiredPolicyId)) {
            LOGGER.info("ISM policy already applied to index template {}", templateName);
            continue;
          }

          final JsonObject newSettings = settingsBuilder.build();

          final JsonObjectBuilder updatedInnerTemplateBuilder =
              Json.createObjectBuilder().add("settings", newSettings);

          for (final String key : innerTemplate.keySet()) {
            if (!"settings".equals(key)) { // do not overwrite new settings
              updatedInnerTemplateBuilder.add(key, innerTemplate.get(key));
            }
          }

          final JsonObject updatedInnerTemplate = updatedInnerTemplateBuilder.build();

          final JsonObjectBuilder updatedTemplateBuilder =
              Json.createObjectBuilder()
                  .add("index_patterns", indexPatterns)
                  .add("template", updatedInnerTemplate);

          for (final String key : template.keySet()) {
            if (!"index_patterns".equals(key) && !"template".equals(key)) {
              updatedTemplateBuilder.add(key, template.get(key));
            }
          }

          final String updatedTemplate = updatedTemplateBuilder.build().toString();
          retryOpenSearchClient.putIndexTemplateSettings(templateName, updatedTemplate);
        }
      }
    }
  }

  private static boolean isPolicyAlreadyApplied(
      final JsonObject existingSettings, final String requiredPolicyId) {

    final JsonObject ismSettings =
        Optional.ofNullable(existingSettings.getJsonObject("index"))
            .map(index -> index.getJsonObject("plugins"))
            .map(plugins -> plugins.getJsonObject("index_state_management"))
            .orElse(null);

    if (ismSettings == null || !ismSettings.containsKey("policy_id")) {
      return false;
    }
    final String currentPolicyId = ismSettings.getString("policy_id", null);
    return Objects.equals(requiredPolicyId, currentPolicyId);
  }
}
