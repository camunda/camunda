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
package io.camunda.tasklist.schema.migration.os;

import static io.camunda.tasklist.util.OpenSearchUtil.joinWithAnd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.MigrationRepositoryIndex;
import io.camunda.tasklist.schema.migration.Step;
import io.camunda.tasklist.schema.migration.StepsRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchStepsRepository implements StepsRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/os/change";

  @Autowired private RetryOpenSearchClient retryOpenSearchClient;

  @Qualifier("tasklistObjectMapper")
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private MigrationRepositoryIndex migrationRepositoryIndex;

  /**
   * Updates Steps in index by comparing steps in json format with documents from index. If there
   * are any new steps then they will be saved in index.
   */
  @Override
  public void updateSteps() throws IOException, MigrationException {
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        LOGGER.info("Add new step {} to repository.", step);
        save(step);
      }
    }
    retryOpenSearchClient.refresh(migrationRepositoryIndex.getFullQualifiedName());
  }

  private List<Step> readStepsFromClasspath() throws IOException {
    final List<Step> steps = new ArrayList<>();

    final List<Resource> resources =
        getResourcesFor(
            OpenSearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER + "/*" + STEP_FILE_EXTENSION);
    for (Resource resource : resources) {
      LOGGER.info("Read step {} ", resource.getFilename());
      steps.add(readStepFromFile(resource.getInputStream()));
    }
    steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);

    return steps;
  }

  private List<Resource> getResourcesFor(final String pattern) {
    final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      return Arrays.asList(resolver.getResources(pattern));
    } catch (IOException e) {
      LOGGER.info("No resources found for {} ", pattern);
      return List.of();
    }
  }

  private Step readStepFromFile(final InputStream is) throws IOException {
    return objectMapper.readValue(is, Step.class);
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step) throws MigrationException, IOException {
    final boolean createdOrUpdated =
        retryOpenSearchClient.createOrUpdateDocument(
            migrationRepositoryIndex.getFullQualifiedName(),
            idFromStep(step),
            CommonUtils.getJsonObjectFromEntity(step));
    if (createdOrUpdated) {
      LOGGER.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(
          String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  protected List<Step> findBy(final Optional<Query> query) {
    final SearchRequest request =
        SearchRequest.of(
            sr -> {
              query.ifPresent(sr::query);
              return sr.index(migrationRepositoryIndex.getFullQualifiedName())
                  .sort(
                      sort ->
                          sort.field(
                              f ->
                                  f.field(Step.VERSION + ".keyword")
                                      .order(
                                          org.opensearch.client.opensearch._types.SortOrder.Asc)))
                  .allowNoIndices(true)
                  .ignoreUnavailable(true)
                  .expandWildcards(ExpandWildcard.Open)
                  .scroll(s -> s.time(RetryOpenSearchClient.SCROLL_KEEP_ALIVE_MS));
            });
    return retryOpenSearchClient.searchWithScroll(request, Step.class, objectMapper);
  }

  /** Returns all stored steps in repository index */
  @Override
  public List<Step> findAll() {
    LOGGER.debug(
        "Find all steps from OpenSearch at {}:{} ",
        tasklistProperties.getOpenSearch().getHost(),
        tasklistProperties.getOpenSearch().getPort());

    return findBy(Optional.empty());
  }

  /** Returns all steps for an index that are not applied yet. */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    LOGGER.debug(
        "Find 'not applied steps' for index {} from OpenSearch at {}:{} ",
        indexName,
        tasklistProperties.getOpenSearch().getHost(),
        tasklistProperties.getOpenSearch().getPort());

    return findBy(
        Optional.of(
            joinWithAnd(
                new TermQuery.Builder()
                    .field(Step.INDEX_NAME + ".keyword")
                    .value(FieldValue.of(indexName)),
                new TermQuery.Builder().field(Step.APPLIED).value(FieldValue.of(false)))));
  }
}
