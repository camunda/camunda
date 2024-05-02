/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    final List<Step> steps = new ArrayList<>();
    final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    try {
      final Resource[] resources =
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
    final SearchRequest request =
        new SearchRequest(getName())
            .source(searchSpec)
            .indicesOptions(IndicesOptions.lenientExpandOpen());
    return retryElasticsearchClient.searchWithScroll(request, Step.class, objectMapper);
  }
}
