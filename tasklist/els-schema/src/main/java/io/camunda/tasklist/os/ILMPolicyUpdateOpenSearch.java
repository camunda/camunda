/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.es.ILMPolicyUpdateElasticSearch;
import io.camunda.tasklist.management.ILMPolicyUpdate;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
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
public class ILMPolicyUpdateOpenSearch extends TasklistOpenSearchProperties
    implements ILMPolicyUpdate {

  private static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final String ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX =
      "^"
          + TasklistOpenSearchProperties.DEFAULT_INDEX_PREFIX
          + "-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";
  private static final Logger LOGGER = LoggerFactory.getLogger(ILMPolicyUpdateElasticSearch.class);
  private static final String TASKLIST_PREFIX_WILDCARD =
      TasklistOpenSearchProperties.DEFAULT_INDEX_PREFIX + "-*";

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Autowired private OpenSearchSchemaManager schemaManager;

  @Override
  public void applyIlmPolicyToAllIndices() throws IOException {
    LOGGER.info("Applying ISM policy to all existent indices");
    final Response policyExists =
        retryOpenSearchClient.getLifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES);
    if (policyExists == null) {
      LOGGER.info("ISM policy does not exist, creating it");
      schemaManager.createIndexLifeCycles();
    }
    // Apply the ISM policy to the index templates
    applyIlmPolicyToIndexTemplate(true);
    final Pattern indexNamePattern = Pattern.compile(ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX);

    final Set<String> response = retryOpenSearchClient.getIndexNames(TASKLIST_PREFIX_WILDCARD);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, TASKLIST_DELETE_ARCHIVED_INDICES);
      }
    }
  }

  @Override
  public void removeIlmPolicyFromAllIndices() throws IOException {
    LOGGER.info("Removing ISM policy to all existent indices");
    final Set<String> response = retryOpenSearchClient.getIndexNames(TASKLIST_PREFIX_WILDCARD);
    applyIlmPolicyToIndexTemplate(false);
    final Pattern indexNamePattern = Pattern.compile(ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, null);
      }
    }
  }

  private void applyIlmPolicyToIndexTemplate(final boolean applyPolicy) throws IOException {
    final JsonArray templates =
        retryOpenSearchClient.getIndexTemplateSettings(
            ILMPolicyUpdateOpenSearch.TASKLIST_PREFIX_WILDCARD);
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
    final JsonObject indexSettings = existingSettings.getJsonObject("index");
    if (indexSettings != null) {
      final JsonObject pluginsSettings = indexSettings.getJsonObject("plugins");
      if (pluginsSettings != null) {
        final JsonObject ismSettings = pluginsSettings.getJsonObject("index_state_management");
        if (ismSettings != null && ismSettings.containsKey("policy_id")) {
          if (requiredPolicyId == null) {
            return ismSettings.isNull("policy_id");
          }
          return requiredPolicyId.equals(ismSettings.getString("policy_id", null));
        }
      }
    }
    return false;
  }
}
