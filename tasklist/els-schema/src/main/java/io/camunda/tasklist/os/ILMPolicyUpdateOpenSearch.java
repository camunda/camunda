/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.manager.OpenSearchSchemaManager;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ILMPolicyUpdateOpenSearch implements ILMPolicyUpdate {

  private static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(ILMPolicyUpdateOpenSearch.class);

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Autowired private OpenSearchSchemaManager schemaManager;

  @Autowired private TasklistProperties tasklistProperties;

  @Override
  public void applyIlmPolicyToAllIndices() throws IOException {
    LOGGER.info("Applying ISM policy to index templates and existing indices");

    // Ensure that the ISM policy exists before applying it to any templates or indices
    schemaManager.createIndexLifeCyclesIfNotExist();

    // Apply the ISM policy to the index templates
    applyIlmPolicyToIndexTemplate(true);

    // Apply the ISM policy to existing indices created from those templates
    applyIlmPolicyToExistingIndices();
  }

  @Override
  public void removeIlmPolicyFromAllIndices() throws IOException {
    LOGGER.info("Removing ISM policy from index templates and existing indices");

    // Remove the ISM policy from the index templates
    applyIlmPolicyToIndexTemplate(false);

    // Remove the ISM policy from existing indices
    removeIlmPolicyFromExistingIndices();
  }

  private void applyIlmPolicyToIndexTemplate(final boolean applyPolicy) throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final JsonArray templates =
        retryOpenSearchClient.getIndexTemplateSettings(taskListIndexWildCard);

    if (templates != null) {
      for (final JsonObject templateData : templates.getValuesAs(JsonObject.class)) {
        final String templateName = templateData.getString("name");
        final JsonObject template = templateData.getJsonObject("index_template");
        final JsonObject innerTemplate = template.getJsonObject("template");

        final JsonObject existingSettings =
            (innerTemplate != null && innerTemplate.containsKey("settings"))
                ? innerTemplate.getJsonObject("settings")
                : Json.createObjectBuilder().build();

        final JsonObjectBuilder settingsBuilder = Json.createObjectBuilder();

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

        if (isPolicyAlreadyAppliedForTemplate(existingSettings, requiredPolicyId)) {
          LOGGER.info(
              "ISM policy already {} index template {}",
              applyPolicy ? "applied to" : "removed from",
              templateName);
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
                .add("index_patterns", template.getJsonArray("index_patterns"))
                .add("template", updatedInnerTemplate);

        for (final String key : template.keySet()) {
          if (!"index_patterns".equals(key) && !"template".equals(key)) {
            updatedTemplateBuilder.add(key, template.get(key));
            LOGGER.info("ISM Policy updated for index template {}", templateName);
          }
        }

        final String updatedTemplate = updatedTemplateBuilder.build().toString();
        retryOpenSearchClient.putIndexTemplateSettings(templateName, updatedTemplate);
      }
    }
  }

  private void applyIlmPolicyToExistingIndices() throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getOpenSearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";

    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);
    final Set<String> response = retryOpenSearchClient.getIndexNames(taskListIndexWildCard);

    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        try {
          // Check if the ISM policy is already applied to the index using the explain API
          if (isPolicyAlreadyAppliedForIndex(indexName, retryOpenSearchClient, false)) {
            LOGGER.info("ISM policy already applied for index {}", indexName);
            continue;
          }

          // Apply the ISM policy to the index
          retryOpenSearchClient.addISMPolicyToIndex(indexName, TASKLIST_DELETE_ARCHIVED_INDICES);
          LOGGER.info("ISM policy updated to index {}", indexName);
        } catch (final IOException e) {
          LOGGER.error("Failed to apply ISM policy for index {}: {}", indexName, e.getMessage());
        }
      }
    }
  }

  private void removeIlmPolicyFromExistingIndices() throws IOException {
    final String taskListIndexWildCard = tasklistProperties.getOpenSearch().getIndexPrefix() + "-*";
    final String archiveTemplatePatterndNameRegex =
        "^"
            + tasklistProperties.getOpenSearch().getIndexPrefix()
            + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";

    final Pattern indexNamePattern = Pattern.compile(archiveTemplatePatterndNameRegex);
    final Set<String> response = retryOpenSearchClient.getIndexNames(taskListIndexWildCard);

    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        if (isPolicyAlreadyAppliedForIndex(indexName, retryOpenSearchClient, true)) {
          LOGGER.info("ISM policy already removed for the index {}", indexName);
        } else {
          retryOpenSearchClient.removeISMPolicyFromIndex(indexName);
          LOGGER.info("ISM policy removed from index {}", indexName);
        }
      }
    }
  }

  private static boolean isPolicyAlreadyAppliedForTemplate(
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

  private static boolean isPolicyAlreadyAppliedForIndex(
      final String indexName,
      final RetryOpenSearchClient retryOpenSearchClient,
      final boolean isRemove)
      throws IOException {

    // Use the ISM explain API to check the current policy applied to the index
    final JsonObject explainResponse = retryOpenSearchClient.getExplainIndexResponse(indexName);

    if (isRemove) {
      return !explainResponse.containsKey("policy_id");
    }

    // Directly check for the presence of the "policy_id" key in the response
    if (explainResponse == null || !explainResponse.containsKey("policy_id")) {
      return false;
    }

    final String currentPolicyId = explainResponse.getString("policy_id", null);
    return Objects.equals(
        ILMPolicyUpdateOpenSearch.TASKLIST_DELETE_ARCHIVED_INDICES, currentPolicyId);
  }
}
