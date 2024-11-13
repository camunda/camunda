/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.migration.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.exceptions.MigrationException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.v86.indices.MigrationRepositoryIndex;
import io.camunda.tasklist.schema.v86.migration.Step;
import io.camunda.tasklist.schema.v86.migration.StepsRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Saves and retrieves Steps from Elasticsearch index.<br>
 * After creation it updates the repository index by looking in classpath folder for new steps.<br>
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchStepsRepository implements StepsRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchStepsRepository.class);

  private static final String STEP_FILE_EXTENSION = ".json";

  private static final String DEFAULT_SCHEMA_CHANGE_FOLDER = "/schema/es/change";

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

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
    retryElasticsearchClient.refresh(migrationRepositoryIndex.getFullQualifiedName());
  }

  private List<Step> readStepsFromClasspath() throws IOException {
    final List<Step> steps = new ArrayList<>();

    final List<Resource> resources =
        getResourcesFor(
            ElasticsearchStepsRepository.DEFAULT_SCHEMA_CHANGE_FOLDER + "/*" + STEP_FILE_EXTENSION);
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
        retryElasticsearchClient.createOrUpdateDocument(
            migrationRepositoryIndex.getFullQualifiedName(),
            idFromStep(step),
            objectMapper.writeValueAsString(step));
    if (createdOrUpdated) {
      LOGGER.info("Step {}  saved.", step);
    } else {
      throw new MigrationException(
          String.format("Error in save step %s:  document wasn't created/updated.", step));
    }
  }

  protected List<Step> findBy(final Optional<QueryBuilder> query) {
    final SearchSourceBuilder searchSpec =
        new SearchSourceBuilder().sort(Step.VERSION + ".keyword", SortOrder.ASC);
    query.ifPresent(searchSpec::query);
    final SearchRequest request =
        new SearchRequest(migrationRepositoryIndex.getFullQualifiedName())
            .source(searchSpec)
            .indicesOptions(IndicesOptions.lenientExpandOpen());
    return retryElasticsearchClient.searchWithScroll(request, Step.class, objectMapper);
  }

  /** Returns all stored steps in repository index */
  @Override
  public List<Step> findAll() {
    LOGGER.debug(
        "Find all steps from Elasticsearch at {}:{} ",
        tasklistProperties.getElasticsearch().getHost(),
        tasklistProperties.getElasticsearch().getPort());

    return findBy(Optional.empty());
  }

  /** Returns all steps for an index that are not applied yet. */
  @Override
  public List<Step> findNotAppliedFor(final String indexName) {
    LOGGER.debug(
        "Find 'not applied steps' for index {} from Elasticsearch at {}:{} ",
        indexName,
        tasklistProperties.getElasticsearch().getHost(),
        tasklistProperties.getElasticsearch().getPort());

    return findBy(
        Optional.ofNullable(
            joinWithAnd(
                termQuery(Step.INDEX_NAME + ".keyword", indexName),
                termQuery(Step.APPLIED, false))));
  }
}
