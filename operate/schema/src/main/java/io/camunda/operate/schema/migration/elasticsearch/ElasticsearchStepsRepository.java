/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import io.camunda.operate.schema.migration.BaseStepsRepository;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.migration.StepsRepository;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Saves and retrieves Steps from Elasticsearch index.<br>
 * After creation, it updates the repository index by looking in classpath folder for new steps.<br>
 */
@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchStepsRepository extends BaseStepsRepository implements StepsRepository {

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/elasticsearch/change";

  private final RetryElasticsearchClient retryElasticsearchClient;

  private final ObjectMapper objectMapper;

  private final OperateProperties operateProperties;

  private final MigrationRepositoryIndex migrationRepositoryIndex;

  @Autowired
  public ElasticsearchStepsRepository(
      final OperateProperties operateProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper,
      final RetryElasticsearchClient retryElasticsearchClient,
      final MigrationRepositoryIndex migrationRepositoryIndex) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
    this.retryElasticsearchClient = retryElasticsearchClient;
    this.migrationRepositoryIndex = migrationRepositoryIndex;
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
        retryElasticsearchClient.createOrUpdateDocument(
            getName(), idFromStep(step), objectMapper.writeValueAsString(step));
    if (createdOrUpdated) {
      logger.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(
          String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  /** Returns all stored steps in repository index */
  @Override
  public List<Step> findAll() {
    logger.debug(
        "Find all steps from Elasticsearch at {}", operateProperties.getElasticsearch().getUrl());
    return findBy(Optional.empty());
  }

  /** Returns all steps for an index that are not applied yet. */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    logger.debug(
        "Find 'not applied steps' for index {} from Elasticsearch at {} ",
        indexName,
        operateProperties.getElasticsearch().getUrl());

    return findBy(
        Optional.ofNullable(
            joinWithAnd(
                termQuery(Step.INDEX_NAME + ".keyword", indexName),
                termQuery(Step.APPLIED, false))));
  }

  /** Returns the of repository. It is used as index name for elasticsearch */
  @Override
  public String getName() {
    return migrationRepositoryIndex.getFullQualifiedName();
  }

  @Override
  public void refreshIndex() {
    retryElasticsearchClient.refresh(getName());
  }

  @Override
  public List<Step> readStepsFromClasspath() throws IOException {
    List<Step> steps = new ArrayList<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      Resource[] resources =
          resolver.getResources(
              ElasticsearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER
                  + "/*"
                  + STEP_FILE_EXTENSION);

      for (Resource resource : resources) {
        logger.info("Read step {} ", resource.getFilename());
        steps.add(readStepFromFile(resource.getInputStream()));
      }
      steps.sort(Step.SEMANTICVERSION_ORDER_COMPARATOR);
      return steps;
    } catch (FileNotFoundException ex) {
      // ignore
      logger.warn("Directory with migration steps was not found: " + ex.getMessage());
    }
    return steps;
  }

  protected List<Step> findBy(final Optional<QueryBuilder> query) {
    final SearchSourceBuilder searchSpec =
        new SearchSourceBuilder().sort(Step.VERSION + ".keyword", SortOrder.ASC);
    query.ifPresent(searchSpec::query);
    SearchRequest request =
        new SearchRequest(getName())
            .source(searchSpec)
            .indicesOptions(IndicesOptions.lenientExpandOpen());
    return retryElasticsearchClient.searchWithScroll(request, Step.class, objectMapper);
  }
}
