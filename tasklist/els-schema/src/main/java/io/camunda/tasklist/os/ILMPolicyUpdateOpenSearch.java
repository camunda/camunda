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
import io.camunda.tasklist.schema.manager.OpenSearchSchemaManager;
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
  private static final String ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX =
      "^tasklist-.*-\\d+\\.\\d+\\.\\d+_\\d{4}-\\d{2}-\\d{2}$";
  private static final Logger LOGGER = LoggerFactory.getLogger(ILMPolicyUpdateElasticSearch.class);
  private static final String TASKLIST_PREFIX = "tasklist-*";

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Autowired private OpenSearchSchemaManager schemaManager;

  @Override
  public void applyIlmPolicyToAllIndices() {
    LOGGER.info("Applying ISM policy to all existent indices");
    final Response policyExists =
        retryOpenSearchClient.getLifecyclePolicy(TASKLIST_DELETE_ARCHIVED_INDICES); // TDB - Test
    if (policyExists == null) {
      LOGGER.info("ISM policy does not exist, creating it"); // TDB - Test
      schemaManager.createIndexLifeCycles();
    }

    final Pattern indexNamePattern = Pattern.compile(ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX);

    final Set<String> response = retryOpenSearchClient.getIndexNames(TASKLIST_PREFIX);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, TASKLIST_DELETE_ARCHIVED_INDICES);
      }
    }
  }

  @Override
  public void removeIlmPolicyFromAllIndices() {
    LOGGER.info("Removing ISM policy to all existent indices");
    final Set<String> response = retryOpenSearchClient.getIndexNames(TASKLIST_PREFIX);
    final Pattern indexNamePattern = Pattern.compile(ARCHIVE_TEMPLATE_PATTERN_NAME_REGEX);
    for (final String indexName : response) {
      if (indexNamePattern.matcher(indexName).matches()) {
        retryOpenSearchClient.putLifeCyclePolicy(indexName, null);
      }
    }
  }
}
