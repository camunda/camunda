/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import io.camunda.operate.es.RetryElasticsearchClient;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.MigrationRepositoryIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
/**
 * Saves and retrieves Steps from Elasticsearch index.<br>
 * After creation it updates the repository index by looking in classpath folder for new steps.<br>
 *
 */
@Component
public class ElasticsearchStepsRepository implements StepsRepository {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/change";

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Qualifier("operateObjectMapper")
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private MigrationRepositoryIndex migrationRepositoryIndex;

  /**
   * Updates Steps in index by comparing steps in json format with documents from index.
   * If there are any new steps then they will be saved in index.
   */
  @Override
  public void updateSteps() throws IOException, MigrationException {
    final List<Step> stepsFromFiles = readStepsFromClasspath();
    final List<Step> stepsFromRepository = findAll();
    for (final Step step : stepsFromFiles) {
      if (!stepsFromRepository.contains(step)) {
        step.setCreatedDate(OffsetDateTime.now());
        logger.info("Add new step {} to repository.", step);
        save(step);
      }
    }
    retryElasticsearchClient.refresh(getName());
  }

  private List<Step> readStepsFromClasspath() throws IOException {
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
      //ignore
      logger.warn("Directory with migration steps was not found: " + ex.getMessage());
    }
    return steps;
  }

  private Step readStepFromFile(final InputStream is) throws IOException {
    return objectMapper.readValue(is, Step.class);
  }

  /**
   * Returns the of repository. It is used as index name for elasticsearch
   */
  @Override
  public String getName() {
    return migrationRepositoryIndex.getFullQualifiedName();
  }

  protected String idFromStep(final Step step) {
    return step.getVersion() + "-" + step.getOrder();
  }

  @Override
  public void save(final Step step) throws MigrationException, IOException {
    final boolean createdOrUpdated = retryElasticsearchClient.createOrUpdateDocument(
        getName(),
        idFromStep(step),
        objectMapper.writeValueAsString(step));
    if (createdOrUpdated) {
      logger.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  protected List<Step> findBy(final Optional<QueryBuilder> query) {
    final SearchSourceBuilder searchSpec = new SearchSourceBuilder()
        .sort(Step.VERSION+ ".keyword", SortOrder.ASC);
    query.ifPresent(searchSpec::query);
    SearchRequest request = new SearchRequest(getName())
        .source(searchSpec)
        .indicesOptions(IndicesOptions.lenientExpandOpen());
    return retryElasticsearchClient.searchWithScroll(request, Step.class, objectMapper);
  }

  /**
   * Returns all stored steps in repository index
   */
  @Override
  public List<Step> findAll() {
    logger.debug("Find all steps from Elasticsearch at {}:{} ", operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort());

    return findBy(Optional.empty());
  }
  /**
   * Returns all steps for an index that are not applied yet.
   */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    logger.debug("Find 'not applied steps' for index {} from Elasticsearch at {}:{} ", indexName,
        operateProperties.getElasticsearch().getHost(), operateProperties.getElasticsearch().getPort());

    return findBy(Optional.ofNullable(
        joinWithAnd(
            termQuery(Step.INDEX_NAME + ".keyword", indexName),
            termQuery(Step.APPLIED, false))));
  }

}
