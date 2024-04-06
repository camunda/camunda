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

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.schema.SemanticVersion;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class IndexSchemaValidatorOpenSearch implements IndexSchemaValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(IndexSchemaValidatorOpenSearch.class);

  private static final Pattern VERSION_PATTERN = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+.*)_.*");

  @Autowired Set<IndexDescriptor> indexDescriptors;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired RetryOpenSearchClient retryOpenSearchClient;

  private Set<String> getAllIndexNamesForIndex(String index) {
    final String indexPattern = String.format("%s-%s*", getIndexPrefix(), index);
    LOGGER.debug("Getting all indices for {}", indexPattern);
    final Set<String> indexNames = retryOpenSearchClient.getIndexNames(indexPattern);
    // since we have indices with similar names, we need to additionally filter index names
    // e.g. task and task-variable
    final String patternWithVersion = String.format("%s-%s-\\d.*", getIndexPrefix(), index);
    return indexNames.stream()
        .filter(n -> n.matches(patternWithVersion))
        .collect(Collectors.toSet());
  }

  private String getIndexPrefix() {
    return tasklistProperties.getOpenSearch().getIndexPrefix();
  }

  public Set<String> newerVersionsForIndex(IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> SemanticVersion.fromVersion(version).isNewerThan(currentVersion))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<String> olderVersionsForIndex(IndexDescriptor indexDescriptor) {
    final SemanticVersion currentVersion =
        SemanticVersion.fromVersion(indexDescriptor.getVersion());
    final Set<String> versions = versionsForIndex(indexDescriptor);
    return versions.stream()
        .filter(version -> currentVersion.isNewerThan(SemanticVersion.fromVersion(version)))
        .collect(Collectors.toSet());
  }

  private Set<String> versionsForIndex(IndexDescriptor indexDescriptor) {
    final Set<String> allIndexNames = getAllIndexNamesForIndex(indexDescriptor.getIndexName());
    return allIndexNames.stream()
        .map(this::getVersionFromIndexName)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toSet());
  }

  private Optional<String> getVersionFromIndexName(String indexName) {
    final Matcher matcher = VERSION_PATTERN.matcher(indexName);
    if (matcher.matches() && matcher.groupCount() > 0) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }

  @Override
  public void validate() {
    if (!hasAnyTasklistIndices()) {
      return;
    }
    final Set<String> errors = new HashSet<>();
    indexDescriptors.forEach(
        indexDescriptor -> {
          final Set<String> oldVersions = olderVersionsForIndex(indexDescriptor);
          final Set<String> newerVersions = newerVersionsForIndex(indexDescriptor);
          if (oldVersions.size() > 1) {
            errors.add(
                String.format(
                    "More than one older version for %s (%s) found: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), oldVersions));
          }
          if (!newerVersions.isEmpty()) {
            errors.add(
                String.format(
                    "Newer version(s) for %s (%s) already exists: %s",
                    indexDescriptor.getIndexName(), indexDescriptor.getVersion(), newerVersions));
          }
        });
    if (!errors.isEmpty()) {
      throw new TasklistRuntimeException("Error(s) in index schema: " + String.join(";", errors));
    }
  }

  @Override
  public boolean hasAnyTasklistIndices() {
    final Set<String> indices =
        retryOpenSearchClient.getIndexNames(
            tasklistProperties.getOpenSearch().getIndexPrefix() + "*");
    return !indices.isEmpty();
  }

  @Override
  public boolean schemaExists() {
    try {
      final Set<String> indices =
          retryOpenSearchClient.getIndexNames(
              tasklistProperties.getOpenSearch().getIndexPrefix() + "*");
      final List<String> allIndexNames =
          map(indexDescriptors, IndexDescriptor::getFullQualifiedName);
      return indices.containsAll(allIndexNames) && validateNumberOfReplicas(allIndexNames);
    } catch (Exception e) {
      LOGGER.error("Check for existing schema failed", e);
      return false;
    }
  }

  public boolean validateNumberOfReplicas(final List<String> indexes) {
    for (String index : indexes) {
      final IndexSettings response =
          retryOpenSearchClient.getIndexSettingsFor(
              index, RetryOpenSearchClient.NUMBERS_OF_REPLICA);
      if (!response
          .numberOfReplicas()
          .equals(String.valueOf(tasklistProperties.getOpenSearch().getNumberOfReplicas()))) {
        return false;
      }
    }
    return true;
  }
}
